import chisel3._

class FetchStage(code: Array[Int], PcStart: Int) extends Module {
    
    val io = IO(new Bundle {
    val pc = Output(UInt(32.W))
    val instr = Output(UInt(32.W))
  })

  val imem = VecInit(code.toIndexedSeq.map(_.U(32.W)))

  //Program counter
  val Pc = RegInit(PcStart.asUInt(32.W))
  Pc := Pc + 4.U

  // Registers
  val pcReg = RegInit(0.U(32.W))
  pcReg := Pc
  val instrReg = RegInit(0.8(32.W))
  instrReg := imem(Pc(31,2))
  
  // Når vi implementerer branching, skal vi ændre Pc så den kan muxes mellem Pc + 4 og en branch target address
  io.pc := pcReg
  io.instr := instrReg
}