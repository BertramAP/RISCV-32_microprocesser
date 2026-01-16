package stages

import chisel3._
import chisel3.util._

class FetchStage(PcStart: Int, memSizeWords: Int = 128) extends Module {
    
  val io = IO(new Bundle {
      val in = Input(new FetchBranchIO)
      val imemAddr = Output(UInt(log2Ceil(memSizeWords).W))
      val imemInstr = Input(UInt(32.W))
      val out = Output(new FetchDecodeIO)

  })


  //Program counter
  val Pc = RegInit((PcStart.toLong & 0xFFFFFFFFL).U(32.W))
  val nextPc = Mux(io.in.branchTaken, io.in.branchTarget, Pc + 4.U)
  Pc := Mux(io.in.done || io.in.stall, Pc, nextPc) // done on Ecall 

  io.out.pc := Pc
  val wordAddr = Pc(31, 2)(log2Ceil(memSizeWords) - 1, 0)
  io.imemAddr := wordAddr
  // Mask PC to avoid out of bounds
  io.out.instr := io.imemInstr
}
