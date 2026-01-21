package stages
import chisel3._
import chisel3.util._

class MemStage(data: Array[Int], memSize: Int = 128) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExecuteMemIO)
    val out = Output(new MemWbIO)
    val dmemWe    = Input(Bool())
    val dmemWaddr = Input(UInt(log2Ceil(memSize).W))
    val dmemWdata = Input(UInt(32.W))
  })
  
  io.out.pc := io.in.pc
  io.out.wbRegWrite := io.in.regWrite
  io.out.wbMemToReg := io.in.memToReg
  io.out.done       := io.in.done
  io.out.funct3     := io.in.funct3

  // Split memory into 4 banks each bank is 1 byte wide
  val mem = SyncReadMem(memSize, Vec(4, UInt(8.W)))
  
  // Address Decoding
  val rEnable = io.in.memRead
  val addr = io.in.aluOut
  val offset = addr(1, 0)
  val wordIdx = addr(log2Ceil(memSize * 4) - 1, 2) // Word index

  // Calculate address for each bank. If bank index < offset, it belongs to the next word.

  // Delay offset to match SyncReadMem latency (1 cycle) for data alignment
  val offsetReg = RegEnable(offset, rEnable)
  
  val rawDataVec = mem.read(wordIdx, rEnable)
  val rawData = rawDataVec.asUInt
  val shiftedData = rawData >> (offsetReg * 8.U)
  val finalData = WireDefault(0.U(32.W))
  
switch(io.in.funct3) {
    is(0.U) { finalData := Cat(Fill(24, shiftedData(7)), shiftedData(7,0)) }  // LB
    is(1.U) { finalData := Cat(Fill(16, shiftedData(15)), shiftedData(15,0)) } // LH
    is(2.U) { finalData := shiftedData }       // LW
    is(4.U) { finalData := shiftedData(7, 0) } // LBU
    is(5.U) { finalData := shiftedData(15, 0) } // LHU
    is(3.U) { finalData := shiftedData } // Default/LBU fallback
  }
  io.out.memData := finalData
  
  val wEnable = WireDefault(false.B)
  val wAddr = Wire(UInt(log2Ceil(memSize).W))
  val wMask = Wire(Vec(4, Bool()))
  val wData = Wire(Vec(4, UInt(8.W)))

  // Default values
  wMask := VecInit(false.B, false.B, false.B, false.B)
  wData := VecInit(0.U(8.W), 0.U(8.W), 0.U(8.W), 0.U(8.W))
  // Data to write (split 32-bit input into 4 bytes)
  val alignedStoreData = io.in.storeData << (offset * 8.U)
  val storeBytes = VecInit(alignedStoreData(7,0), alignedStoreData(15,8), alignedStoreData(23,16), alignedStoreData(31,24))
  val uartBytes  = VecInit(io.dmemWdata(7,0), io.dmemWdata(15,8), io.dmemWdata(23,16), io.dmemWdata(31,24))

  // dmemWe writes aligned 32-bit words
  when(io.dmemWe) {
    wEnable := true.B
    wMask := VecInit(true.B, true.B, true.B, true.B)
    wData := uartBytes
    wAddr := io.dmemWaddr
    } .elsewhen(io.in.memWrite) {
    wEnable := true.B
    wAddr := wordIdx
    wData := storeBytes
    
    // Set masks based on alignment (offset) and size (funct3)
    // Default Mask
    wMask := VecInit(false.B, false.B, false.B, false.B)
    switch(io.in.funct3) {
      is(0.U) { wMask(offset) := true.B }                            // SB
      is(1.U) { wMask(offset) := true.B; wMask(offset + 1.U) := true.B } // SH
      is(2.U) { wMask := VecInit(true.B, true.B, true.B, true.B) }   // SW
    }
  }. otherwise {
    wEnable := false.B
    wAddr := 0.U
    wData := VecInit(0.U(8.W), 0.U(8.W), 0.U(8.W), 0.U(8.W))
    wMask := VecInit(false.B, false.B, false.B, false.B)  
  }
  when (wEnable) {
    mem.write(wAddr, wData, wMask)
  }
  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done 
}