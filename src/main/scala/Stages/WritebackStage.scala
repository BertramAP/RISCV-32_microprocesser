
package stages

import chisel3._

class WritebackStage extends Module {
  val io = IO(new Bundle {
    // from MEM/WB

    val aluData    = Input(UInt(32.W))   // ALU result from EX stage
    val memData    = Input(UInt(32.W))   // load data from memory
    val memToReg   = Input(Bool())       // 1=write memData, 0=write aluData

    val wbRd       = Input(UInt(5.W))
    val wbRegWrite = Input(Bool())

    // to Register File
    val rfWriteData = Output(UInt(32.W))
    val rfWriteRd   = Output(UInt(5.W))
    val rfRegWrite  = Output(Bool())
  })
  io.rfWriteData := Mux(io.memToReg, io.memData, io.aluData)
  io.rfWriteRd   := io.wbRd
  io.rfRegWrite  := io.wbRegWrite && (io.wbRd =/= 0.U)
}
