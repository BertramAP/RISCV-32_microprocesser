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
  io.out.funct3     := io.in.funct3 // Pass funct3 to WB

  // Use Vec of 4 bytes for masked writes
  val memory = SyncReadMem(memSize, Vec(4, UInt(8.W)))
    
  when(io.dmemWe) {
    // Convert 32-bit write data to Vec of bytes
    val dmemByte0 = io.dmemWdata(7, 0)
    val dmemByte1 = io.dmemWdata(15, 8)
    val dmemByte2 = io.dmemWdata(23, 16)
    val dmemByte3 = io.dmemWdata(31, 24)
    memory.write(io.dmemWaddr, VecInit(dmemByte0, dmemByte1, dmemByte2, dmemByte3))
  }.elsewhen(io.in.memWrite) {
    // Handled below in store logic
  }

  // Address decoding
  val wordIndex = io.in.aluOut >> 2
  val effectiveAddr = wordIndex(log2Ceil(memSize)-1, 0)
  val last = (memSize - 1).U
  val nextAddr = Mux(effectiveAddr === last, last, effectiveAddr + 1.U)
  
  // Data Read Logic (Synchronous)
  val lowerVec = memory.read(effectiveAddr)
  val upperVec = memory.read(nextAddr)
  
  // Convert Vecs back to UInt words
  val lowerWord = Cat(lowerVec(3), lowerVec(2), lowerVec(1), lowerVec(0))
  val upperWord = Cat(upperVec(3), upperVec(2), upperVec(1), upperVec(0))
  
  // Output raw double-word. Alignment happens in Writeback stage.
  io.out.memData := Cat(upperWord, lowerWord)
  
  // Store Logic
  val offset = io.in.aluOut(1, 0)
  
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
    
    // Convert logic to Vec and Bool Mask
    // writeDataLow is 32-bit. Split into bytes.
    val wDataLowVec = VecInit(writeDataLow(7,0), writeDataLow(15,8), writeDataLow(23,16), writeDataLow(31,24))
    // Check LSB of each byte mask to determine if byte should be written (Mask is FF or 00)
    val wMaskLowVec = VecInit(writeMaskLow(0), writeMaskLow(8), writeMaskLow(16), writeMaskLow(24)) 
    
     if (true) { // scope
         memory.write(effectiveAddr, wDataLowVec, wMaskLowVec)
     }
    
    val wDataHighVec = VecInit(writeDataHigh(7,0), writeDataHigh(15,8), writeDataHigh(23,16), writeDataHigh(31,24))
    val wMaskHighVec = VecInit(writeMaskHigh(0), writeMaskHigh(8), writeMaskHigh(16), writeMaskHigh(24))
    
    if (true) {
        memory.write(nextAddr, wDataHighVec, wMaskHighVec)
    }
  }
  
  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done 
}