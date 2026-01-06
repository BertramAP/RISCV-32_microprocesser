import chisel3._

class FetchStage(code: Array[Int], PcStart: Int) extends Module {
    
    val io = IO(new Bundle {
    val pc = Output(UInt(32.W))
    val instr = Output(UInt(32.W))
  })

  val imem = VecInit(code.toIndexedSeq.map(_.U(32.W)))

  //Program counter
  val Pc = RegInit(PcStart.asUInt(32.W))

  io.pc := Pc
  io.instr := imem(Pc(31,2))

  Pc := Pc + 4.U
}