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

  // Data Alignment Logic (moved from MemStage)
  val offset = io.in.aluOut(1, 0)
  val alignedWord = io.in.memData >> (offset * 8.U)
  val readData = alignedWord(31, 0)
  
  val memData = WireDefault(0.U(32.W))
  
  switch(io.in.funct3) {
    is(0.U) { memData := readData(7, 0).asSInt.pad(32).asUInt }  // LB
    is(1.U) { memData := readData(15, 0).asSInt.pad(32).asUInt } // LH
    is(2.U) { memData := readData }                              // LW
    is(4.U) { memData := readData(7, 0) }                        // LBU
    is(5.U) { memData := readData(15, 0) }                       // LHU
    is(3.U) { memData := readData }                              // Default to LW
  }

  io.rfWriteData := Mux(io.in.wbMemToReg, memData, io.in.aluOut)
  io.rfWriteRd   := io.in.wbRd
  io.rfRegWrite  := io.in.wbRegWrite && (io.in.wbRd =/= 0.U)
  io.done        := io.in.done
}
