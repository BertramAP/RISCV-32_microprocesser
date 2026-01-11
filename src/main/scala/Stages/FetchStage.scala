package stages

import chisel3._

class FetchStage(code: Array[Int], PcStart: Int) extends Module {
    
  val io = IO(new Bundle {
      val in = Input(new FetchBranchIO)
      val out = Output(new FetchDecodeIO)
  })

  val imem = VecInit(code.toIndexedSeq.map { word =>
    (word & 0xFFFFFFFFL).U(32.W)
  })

  //Program counter
  val Pc = RegInit(PcStart.asUInt(32.W))
  val nextPc = Mux(io.in.branchTaken, io.in.branchTarget, Pc + 4.U)
  Pc := Mux(io.in.done, Pc, nextPc)

  io.out.pc := Pc
  io.out.instr := imem(Pc(31,2))
}
