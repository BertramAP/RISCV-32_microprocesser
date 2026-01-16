package stages
import chisel3._
import chisel3.util._

class MemStage(data: Array[Int], memSize: Int = 128) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExecuteMemIO)
    val out = Output(new MemWbIO)
    //val dbgMem = Output(Vec(memSize, UInt(32.W)))
    val dmemWe    = Input(Bool())
    val dmemWaddr = Input(UInt(log2Ceil(memSize).W))
    val dmemWdata = Input(UInt(32.W))
  })
  
  io.out.wbRegWrite := io.in.regWrite
  io.out.wbMemToReg := io.in.memToReg
  io.out.done       := io.in.done

  val memory = SyncReadMem(memSize, UInt(32.W))
    
  when(io.dmemWe) {
    memory.write(io.dmemWaddr, io.dmemWdata)
  }.elsewhen(io.in.memWrite) {
    // Handled below in store logic
  }

  // Address decoding
  val wordIndex = io.in.aluOut >> 2
  val effectiveAddr = wordIndex(log2Ceil(memSize)-1, 0)
  val offset = io.in.aluOut(1, 0)
  
  // Data Read Logic
  val lowerWord = memory.read(effectiveAddr)
  val last = (memSize - 1).U
  val nextAddr = Mux(effectiveAddr === last, last, effectiveAddr + 1.U)
  val upperWord = memory.read(nextAddr)
  
  val doubleWord = Cat(upperWord, lowerWord)
  val alignedWord = doubleWord >> (offset * 8.U)
  val readData = alignedWord(31, 0)
  
  val memData = WireDefault(0.U(32.W))

  // Load Logic
  switch(io.in.funct3) {
    is(0.U) { memData := readData(7, 0).asSInt.pad(32).asUInt }  // LB
    is(1.U) { memData := readData(15, 0).asSInt.pad(32).asUInt } // LH
    is(2.U) { memData := readData }                              // LW
    is(4.U) { memData := readData(7, 0) }                        // LBU
    is(5.U) { memData := readData(15, 0) }                       // LHU
    is(3.U) { memData := readData }                              // Default to LW
  }
  
  // Store Logic
  when(io.in.memWrite && !io.dmemWe) {
    val storeData = io.in.storeData
    val writeMask = WireDefault(0.U(32.W))
    switch(io.in.funct3) {
      is(0.U) { writeMask := "hFF".U }       // SB
      is(1.U) { writeMask := "hFFFF".U }     // SH
      is(2.U) { writeMask := "hFFFFFFFF".U } // SW
      is(3.U) { writeMask := "hFFFFFFFF".U } // Default
    }
    
    val writeData64 = (storeData.asUInt & writeMask) << (offset * 8.U)
    val writeMask64 = writeMask.asUInt << (offset * 8.U)
    
    val writeDataLow = writeData64(31, 0)
    val writeMaskLow = writeMask64(31, 0)
    
    val writeDataHigh = writeData64(63, 32)
    val writeMaskHigh = writeMask64(63, 32)
    
    when(writeMaskLow =/= 0.U) {
        memory.write(effectiveAddr, (lowerWord & ~writeMaskLow) | writeDataLow)
    }
    
    when(writeMaskHigh =/= 0.U) {
        memory.write(nextAddr, (upperWord & ~writeMaskHigh) | writeDataHigh)
    }
  }
  
  io.out.memData := memData
  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done // Should be also be determined by if there is any instruction memory left
  //io.dbgMem := dmem
}