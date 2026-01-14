package stages

import chisel3._
import chisel3.util._

class FetchStage(code: Array[Int], PcStart: Int, memSize: Int = 128) extends Module {
    
  val io = IO(new Bundle {
      val in = Input(new FetchBranchIO)
      val out = Output(new FetchDecodeIO)
  })

  // Pad code with zeros up to memSize
  val imemInit = code.toIndexedSeq.map(x => (x & 0xFFFFFFFFL).U(32.W))
  val imem = VecInit(imemInit.take(memSize))

  //Program counter
  val Pc = RegInit(PcStart.asUInt(32.W))
  val nextPc = Mux(io.in.branchTaken, io.in.branchTarget, Pc + 4.U)
  Pc := Mux(io.in.done || io.in.stall, Pc, nextPc)

  io.out.pc := Pc
  // Mask PC to avoid out of bounds
  val addr = Pc(31,2)
  io.out.instr := imem(addr(log2Ceil(memSize)-1, 0))
}
