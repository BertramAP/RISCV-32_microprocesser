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
  io.out.funct3     := io.in.funct3 // Pass funct3 to WB

  // Use Vec of 4 bytes for masked writes
  val memory = SyncReadMem(memSize, Vec(4, UInt(8.W)))
  
  when(io.dmemWe) {
    // Convert 32-bit write data to Vec of bytes
    val dmemByte0 = io.dmemWdata(7, 0)
    val dmemByte1 = io.dmemWdata(15, 8)
    val dmemByte2 = io.dmemWdata(23, 16)
    val dmemByte3 = io.dmemWdata(31, 24)
    memory.write(io.dmemWaddr, VecInit(dmemByte0, dmemByte1, dmemByte2, dmemByte3), VecInit(Seq.fill(4)(true.B)))
  }.elsewhen(io.in.memWrite) {
    // Handled below in store logic
  }

  // Address decoding
  val wordIndex = io.in.aluOut >> 2
  val effectiveAddr = wordIndex(log2Ceil(memSize)-1, 0)
  
  // Data Read Logic (Synchronous)
  val readVec = memory.read(effectiveAddr)
  // Combine bytes to 32-bit word, will be formatted in WB stage
  io.out.memData := Cat(readVec(3), readVec(2), readVec(1), readVec(0)) 
  
  // Store Logic
  when(io.in.memWrite && !io.dmemWe) {
    val storeData = io.in.storeData
    val writeMask = WireDefault(0.U(32.W))
    val offset = io.in.aluOut(1, 0) // Should be 0 for aligned accesses mostly, but we handle byte/half within word
    
    // Calculate 32-bit mask shifted by offset
    switch(io.in.funct3) {
      is(0.U) { writeMask := "h000000FF".U(32.W) << (offset * 8.U) }       // SB
      is(1.U) { writeMask := "h0000FFFF".U(32.W) << (offset * 8.U) }     // SH
      is(2.U) { writeMask := "hFFFFFFFF".U(32.W) }                   // SW (Assume aligned)
      is(3.U) { writeMask := "hFFFFFFFF".U(32.W) } 
    }
    
    val shiftedData = (storeData.asUInt << (offset * 8.U)).asUInt
    
    // Split into bytes for Vec write
    val wDataVec = VecInit(shiftedData(7,0), shiftedData(15,8), shiftedData(23,16), shiftedData(31,24))
    val wMaskVec = VecInit(writeMask(0), writeMask(8), writeMask(16), writeMask(24))
    
    memory.write(effectiveAddr, wDataVec, wMaskVec)
  }
  
  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done 
}