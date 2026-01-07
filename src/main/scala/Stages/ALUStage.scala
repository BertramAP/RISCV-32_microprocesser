package stages

import chisel3._
import chisel3.util._

class ALUStage extends Module {
  val io = IO(new Bundle {
    val in = Input(new DecodeExecuteIO)
    val aluOut = Output(UInt(32.W))
  })

  // Default to addition for now to satisfy the pipeline
  io.aluOut := io.in.src1 + io.in.src2
}