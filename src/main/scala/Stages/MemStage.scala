package stages
import chisel3._
import chisel3.util._

class MemStage(code: Array[Int], memSize: Int = 2048) extends Module {
  val io = IO(new Bundle {
    // from EX/MEM
    val in = Input(new ExecuteMemIO)
    // to MEM/WB
    val out = Output(new MemWbIO)
    val dbgMem = Output(Vec(memSize, UInt(32.W)))
  })
  // Forward control signals
  io.out.wbRegWrite := io.in.regWrite
  io.out.wbMemToReg := io.in.memToReg
  io.out.done       := io.in.done
  
  val memInit = code.toIndexedSeq.map(x => (x & 0xFFFFFFFFL).U(32.W)) ++ Seq.fill(math.max(0, memSize - code.length))(0.U(32.W))
  val dmem = RegInit(VecInit(memInit.take(memSize)))
  // Calculate address within memory bounds
  val addr = io.in.addrWord(log2Ceil(memSize)-1, 0)
  val offset = io.in.aluOut(1, 0)
  val readWord = dmem(addr)
  val memData = WireDefault(0.U(32.W))

  // Load Logic
  switch(io.in.funct3) {
    is(0.U) { memData := (readWord >> (offset * 8.U))(7, 0).asSInt.pad(32).asUInt } // LB
    is(1.U) { memData := (readWord >> (offset * 8.U))(15, 0).asSInt.pad(32).asUInt } // LH
    is(2.U) { memData := readWord } // LW
    is(4.U) { memData := (readWord >> (offset * 8.U))(7, 0) } // LBU (Zero extend)
    is(5.U) { memData := (readWord >> (offset * 8.U))(15, 0) } // LHU
    is(3.U) { memData := readWord } // Default to LW
  }

  // Store Logic
  when(io.in.memWrite) {
    val wdata = WireDefault(io.in.storeData)
    switch(io.in.funct3) {
      is(0.U) { // SB
        val mask = ~(255.U(32.W) << (offset * 8.U))
        val byteVal = (io.in.storeData(7, 0) << (offset * 8.U))
        wdata := (readWord & mask) | byteVal
      }
      is(1.U) { // SH
        val mask = ~(65535.U(32.W) << (offset * 8.U))
        val halfVal = (io.in.storeData(15, 0) << (offset * 8.U))
        wdata := (readWord & mask) | halfVal
      }
      // is(2.U) SW - Default
    }
    dmem(addr) := wdata
  }
  
  io.out.memData := memData
  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done
  io.dbgMem := dmem
}
