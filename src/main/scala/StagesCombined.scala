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

    val mem_wbData  = Output(UInt(32.W))
    val mem_rd      = Output(UInt(5.W))
    val mem_regWrite= Output(Bool())

    val wb_wdata    = Output(UInt(32.W))
    val wb_rd       = Output(UInt(5.W))
    val wb_we       = Output(Bool())
  })

  val fetchStage = Module(new FetchStage(code, PcStart))
  val wbRd = WireDefault(0.U(5.W))
  val wbWd = WireDefault(0.U(32.W))
  val wbRw = WireDefault(false.B)
  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  ifIdReg := fetchStage.io.out
  io.if_pc := fetchStage.io.out.pc
  io.if_instr := fetchStage.io.out.instr

  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in := ifIdReg
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := decodeStage.io.out.src1
  registerFile.io.readRegister2 := decodeStage.io.out.src2
  registerFile.io.writeRegister := wbRd
  registerFile.io.writeData := wbWd
  registerFile.io.regWrite := wbRw

  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  idExReg := decodeStage.io.out
  idExReg.src1 := Mux(decodeStage.io.out.isPC, decodeStage.io.out.pc, registerFile.io.readData1)
  idExReg.src2 := registerFile.io.readData2

  val executeStage = Module(new ExecuteStage())
  executeStage.io.in := idExReg
  io.ex_aluOut := executeStage.io.out.aluOut

  
  // EX/MEM pipeline registers (simple set for current minimal pipeline)
  val exMemAluOut = RegInit(0.U(32.W))
  val exMemRd = RegInit(0.U(5.W))
  val exMemRegWrite = RegInit(false.B)
  exMemAluOut := executeStage.io.out.aluOut
  exMemRd := idExReg.dest(4,0)
  exMemRegWrite := false.B

  
  val memStage = Module(new MemStage())
  memStage.io.in.aluOut := exMemAluOut
  memStage.io.in.addrWord := exMemAluOut(4, 2)
  memStage.io.in.storeData := exMemAluOut
  memStage.io.in.memRead := false.B
  memStage.io.in.memWrite := false.B
  memStage.io.in.rd := exMemRd
  memStage.io.in.regWrite := exMemRegWrite
  memStage.io.in.memToReg := false.B
  
  // MEM/WB pipeline registers
  val memWbData = RegInit(0.U(32.W))
  val memWbRd = RegInit(0.U(5.W))
  val memWbRegWrite = RegInit(false.B)
  val memWbMemToReg = RegInit(false.B)
  memWbData := memStage.io.out.memData
  memWbRd := memStage.io.out.wbRd
  memWbRegWrite := memStage.io.out.wbRegWrite
  memWbMemToReg := memStage.io.out.wbMemToReg
  
  val wbStage = Module(new WritebackStage())
  wbStage.io.aluData := exMemAluOut
  wbStage.io.memData := memWbData
  wbStage.io.memToReg := memWbMemToReg
  wbStage.io.wbRd := memWbRd
  wbStage.io.wbRegWrite := memWbRegWrite
  

  wbRd := wbStage.io.rfWriteRd
  wbWd := wbStage.io.rfWriteData
  wbRw := wbStage.io.rfRegWrite
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
