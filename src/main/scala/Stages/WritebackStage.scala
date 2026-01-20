package stages

import chisel3._
import chisel3.util._

class WritebackStage extends Module {
  val io = IO(new Bundle {
    val in = Input(new MemWbIO)

    val rfWriteData = Output(UInt(32.W))
    val rfWriteRd   = Output(UInt(5.W))
    val rfRegWrite  = Output(Bool())
    val done        = Output(Bool())
  })

 // Data Alignment Logic
val offset      = io.in.aluOut(1, 0)
val readData    = io.in.memData
val shiftedData = readData >> (offset * 8.U)

val memData = WireDefault(0.U(32.W))

switch(io.in.funct3) {
  is("b000".U) { memData := Cat(Fill(24, shiftedData(7)), shiftedData(7, 0)) }   // LB (Explicit Sign Extended)
  is("b001".U) { memData := Cat(Fill(16, shiftedData(15)), shiftedData(15, 0)) } // LH (Explicit Sign Extended)
  is("b010".U) { memData := readData }                                   // LW
  is("b100".U) { memData := shiftedData(7, 0) }                          // LBU (Zero Extended by default)
  is("b101".U) { memData := shiftedData(15, 0) }                         // LHU (Zero Extended by default)
}

  io.rfWriteData := Mux(io.in.wbMemToReg, memData, io.in.aluOut)
  io.rfWriteRd   := io.in.wbRd
  io.rfRegWrite  := io.in.wbRegWrite && (io.in.wbRd =/= 0.U)
  io.done        := io.in.done

}
