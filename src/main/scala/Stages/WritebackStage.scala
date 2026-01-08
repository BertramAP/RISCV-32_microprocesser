package stages

import chisel3._

class WritebackStage extends Module {
  val io = IO(new Bundle {
    val in = Input(new MemWbIO)

    val rfWriteData = Output(UInt(32.W))
    val rfWriteRd   = Output(UInt(5.W))
    val rfRegWrite  = Output(Bool())
  })

  io.rfWriteData := Mux(io.in.wbMemToReg, io.in.memData, io.in.aluOut)
  io.rfWriteRd   := io.in.wbRd
  io.rfRegWrite  := io.in.wbRegWrite && (io.in.wbRd =/= 0.U)
}
