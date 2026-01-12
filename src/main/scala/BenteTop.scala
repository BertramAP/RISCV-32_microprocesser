package stages

import chisel3._
import chisel3.util._

class BenteTop(code: Array[Int], PcStart: Int) extends Module {
  val io = IO(new Bundle {
    // debug outputs for each stage
    val if_pc        = Output(UInt(32.W))
    val if_instr     = Output(UInt(32.W))

    val ifid_instr   = Output(UInt(32.W))

    val id_readAddress1 = Output(UInt(5.W))
    val id_readData1 = Output(UInt(32.W))
    val id_rd        = Output(UInt(5.W))
    val id_imm       = Output(UInt(32.W))
    val id_regWrite  = Output(Bool())
    val id_wbEnable  = Output(Bool()) // For debugging writeback

    val ex_aluOut    = Output(UInt(32.W))
    val ex_rd        = Output(UInt(5.W))
    val ex_regWrite  = Output(Bool())
    val ex_wbEnable  = Output(Bool()) // For debugging writeback
    val ex_branchTaken = Output(Bool())
    val ex_branchTarget = Output(UInt(32.W))

    val mem_ALUOut  = Output(UInt(32.W))
    val mem_rd      = Output(UInt(5.W))
    val mem_regWrite= Output(Bool())
    val mem_wbEnable= Output(Bool()) // For debugging writeback

    val wb_wdata    = Output(UInt(32.W))
    val wb_rd       = Output(UInt(5.W))
    val wb_wbEnable = Output(Bool())

    val done = Output(Bool())
    val debug_regFile = Output(Vec(32, UInt(32.W)))

    // Board outputs
    val led = Output(Bool())

    // Board UART
    val uartRx = Input(Bool())
    val uartTx = Output(Bool())
  })
  val done = WireDefault(false.B)
  io.done := done

  val fetchStage = Module(new FetchStage(code, PcStart))
  fetchStage.io.in.done := done

  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  ifIdReg := fetchStage.io.out
  io.if_pc := fetchStage.io.out.pc
  io.if_instr := fetchStage.io.out.instr

  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in := ifIdReg
  done := decodeStage.io.out.done
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := decodeStage.io.out.src1
  registerFile.io.readRegister2 := decodeStage.io.out.src2

  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  idExReg := decodeStage.io.out
  idExReg.src1 := Mux(decodeStage.io.out.isPC, decodeStage.io.out.pc, registerFile.io.readData1)
  idExReg.src2 := registerFile.io.readData2

  val executeStage = Module(new ExecuteStage())
  executeStage.io.in := idExReg
  io.ex_aluOut := executeStage.io.out.aluOut
  fetchStage.io.in.branchTaken := executeStage.io.BranchOut.branchTaken
  fetchStage.io.in.branchTarget := executeStage.io.BranchOut.branchTarget

  
  // EX/MEM pipeline registers (simple set for current minimal pipeline)
  val exMemReg = RegInit(0.U.asTypeOf(new ExecuteMemIO))
  exMemReg := executeStage.io.out
    
  val memStage = Module(new MemStage())
  memStage.io.in := exMemReg
  
  // MEM/WB pipeline registers
  val memWriteBackReg = RegInit(0.U.asTypeOf(new MemWbIO))
  memWriteBackReg := memStage.io.out
  
  val writeBackStage = Module(new WritebackStage())
  writeBackStage.io.in := memWriteBackReg

  registerFile.io.writeRegister := writeBackStage.io.rfWriteRd
  registerFile.io.writeData := writeBackStage.io.rfWriteData
  registerFile.io.regWrite := writeBackStage.io.rfRegWrite

  // Debug outputs
  io.uartTx := false.B // Not implemented

  io.ifid_instr := ifIdReg.instr

  io.id_readAddress1 := registerFile.io.readRegister1
  io.id_readData1 := registerFile.io.readData1
  io.id_rd := idExReg.dest(4,0)
  io.id_imm := decodeStage.io.out.imm
  io.id_regWrite := decodeStage.io.out.RegWrite
  io.debug_regFile := registerFile.io.debug_registers

  io.ex_rd := executeStage.io.out.rd
  io.ex_regWrite := executeStage.io.out.regWrite
  io.ex_branchTaken := executeStage.io.BranchOut.branchTaken
  io.ex_branchTarget := executeStage.io.BranchOut.branchTarget

  io.mem_ALUOut := memStage.io.in.aluOut
  io.mem_rd := memStage.io.in.rd
  io.mem_regWrite := memStage.io.in.regWrite

  io.wb_wdata := writeBackStage.io.rfWriteData
  io.wb_rd := writeBackStage.io.rfWriteRd 
  io.led := false.B

  // For debugging writeback stage
  io.id_wbEnable := decodeStage.io.out.RegWrite
  io.ex_wbEnable := executeStage.io.out.regWrite
  io.mem_wbEnable := memStage.io.out.wbRegWrite
  io.wb_wbEnable := writeBackStage.io.rfRegWrite
  
}
object StagesCombined extends App {
  emitVerilog(new BenteTop(Array(0x00000013), 0), Array("--target-dir", "generated"))
}