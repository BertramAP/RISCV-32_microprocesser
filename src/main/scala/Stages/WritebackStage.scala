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

 val readData    = io.in.memData
 val memData = WireDefault(0.U(32.W))

 switch(io.in.funct3) {
   is("b000".U) { memData := Cat(Fill(24, readData(7)), readData(7, 0)) }   // LB
   is("b001".U) { memData := Cat(Fill(16, readData(15)), readData(15, 0)) } // LH
   is("b010".U) { memData := readData }                                   // LW
   is("b100".U) { memData := readData(7, 0) }                             // LBU
   is("b101".U) { memData := readData(15, 0) }                            // LHU
 }

  io.rfWriteData := Mux(io.in.wbMemToReg, memData, io.in.aluOut)
  io.rfWriteRd   := io.in.wbRd
  io.rfRegWrite  := io.in.wbRegWrite && (io.in.wbRd =/= 0.U)
  io.done        := io.in.done

}
