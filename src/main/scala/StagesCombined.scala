package stages

import chisel3._
import chisel3.util._

class AddiPipelineTop(code: Array[Int], PcStart: Int) extends Module {
  val io = IO(new Bundle {
    // debug outputs for each stage
    val if_pc        = Output(UInt(32.W))
    val if_instr     = Output(UInt(32.W))

    val ifid_instr   = Output(UInt(32.W))

    val id_rs1       = Output(UInt(5.W))
    val id_rd        = Output(UInt(5.W))
    val id_imm       = Output(UInt(32.W))
    val id_regWrite  = Output(Bool())

    val ex_aluOut    = Output(UInt(32.W))
    val ex_rd        = Output(UInt(5.W))
    val ex_regWrite  = Output(Bool())

    val mem_wbData   = Output(UInt(32.W))
    val mem_rd       = Output(UInt(5.W))
    val mem_regWrite = Output(Bool())

    val wb_wdata     = Output(UInt(32.W))
    val wb_rd        = Output(UInt(5.W))
    val wb_we        = Output(Bool())
  })

  val fetchStage = Module(new FetchStage(code, PcStart))

  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  ifIdReg := fetchStage.io.out
  io.if_pc := fetchStage.io.out.pc
  io.if_instr := fetchStage.io.out.instr

  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in := ifIdReg
  
  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  idExReg := decodeStage.io.out

  val aluStage = Module(new ALUStage())
  aluStage.io.in := idExReg
  io.ex_aluOut := aluStage.io.aluOut

  // EX/MEM pipeline registers (simple set for current minimal pipeline)
  val exMemAluOut = RegInit(0.U(32.W))
  val exMemRd = RegInit(0.U(5.W))
  val exMemRegWrite = RegInit(false.B)
  exMemAluOut := aluStage.io.aluOut
  exMemRd := idExReg.dest(4,0)
  exMemRegWrite := false.B

  val memStage = Module(new MemStage())
  memStage.io.aluOutIn := exMemAluOut
  memStage.io.addrWord := exMemAluOut(4,2)
  memStage.io.storeData := exMemAluOut
  memStage.io.memRead := false.B
  memStage.io.memWrite := false.B
  memStage.io.rd := exMemRd
  memStage.io.regWrite := exMemRegWrite
  memStage.io.memToReg := false.B
  
  // MEM/WB pipeline registers
  val memWbData = RegInit(0.U(32.W))
  val memWbRd = RegInit(0.U(5.W))
  val memWbRegWrite = RegInit(false.B)
  val memWbMemToReg = RegInit(false.B)
  memWbData := memStage.io.memDataOut
  memWbRd := memStage.io.wbRd
  memWbRegWrite := memStage.io.wbRegWrite
  memWbMemToReg := memStage.io.wbMemToReg
  
  val wbStage = Module(new WritebackStage())
  wbStage.io.aluData := exMemAluOut
  wbStage.io.memData := memWbData
  wbStage.io.memToReg := memWbMemToReg
  wbStage.io.wbRd := memWbRd
  wbStage.io.wbRegWrite := memWbRegWrite
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := 0.U
  registerFile.io.readRegister2 := 0.U
  registerFile.io.writeRegister := wbStage.io.rfWriteRd
  registerFile.io.writeData := wbStage.io.rfWriteData
  registerFile.io.regWrite := wbStage.io.rfRegWrite


  // Debug outputs
  io.ifid_instr := ifIdReg.instr

  io.id_rs1 := 0.U
  io.id_rd := idExReg.dest(4,0)
  io.id_imm := idExReg.src2
  io.id_regWrite := exMemRegWrite

  io.ex_rd := exMemRd
  io.ex_regWrite := exMemRegWrite

  io.mem_wbData := memWbData
  io.mem_rd := memWbRd
  io.mem_regWrite := memWbRegWrite

  io.wb_wdata := wbStage.io.rfWriteData
  io.wb_rd := wbStage.io.rfWriteRd
  io.wb_we := wbStage.io.rfRegWrite  

}
