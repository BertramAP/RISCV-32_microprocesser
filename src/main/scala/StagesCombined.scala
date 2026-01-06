package stages

import chisel3._
import chisel3.util._

class AddiPipelineTop extends Module {
  val io = IO(new Bundle {
    // debug outputs for each stage
    val if_pc       = Output(UInt(32.W))
    val if_instr    = Output(UInt(32.W))

    val ifid_instr  = Output(UInt(32.W))

    val id_rs1      = Output(UInt(5.W))
    val id_rd       = Output(UInt(5.W))
    val id_imm      = Output(UInt(32.W))
    val id_regWrite = Output(Bool())

    val ex_aluOut   = Output(UInt(32.W))
    val ex_rd       = Output(UInt(5.W))
    val ex_regWrite = Output(Bool())

    val mem_wbData  = Output(UInt(32.W))
    val mem_rd      = Output(UInt(5.W))
    val mem_regWrite= Output(Bool())

    val wb_wdata    = Output(UInt(32.W))
    val wb_rd       = Output(UInt(5.W))
    val wb_we       = Output(Bool())
  })
}