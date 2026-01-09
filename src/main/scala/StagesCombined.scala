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
    val id_wbEnable  = Output(Bool()) // For debugging writeback

    val ex_aluOut    = Output(UInt(32.W))
    val ex_rd        = Output(UInt(5.W))
    val ex_regWrite  = Output(Bool())
    val ex_wbEnable  = Output(Bool()) // For debugging writeback

    val mem_wbData  = Output(UInt(32.W))
    val mem_rd      = Output(UInt(5.W))
    val mem_regWrite= Output(Bool())
    val mem_wbEnable= Output(Bool()) // For debugging writeback

    val wb_wdata    = Output(UInt(32.W))
    val wb_rd       = Output(UInt(5.W))
    val wb_wbEnable = Output(Bool())

    // Board outputs
    val led = Output(Bool())
  })
  val done = WireDefault(false.B)

  val fetchStage = Module(new FetchStage(code, PcStart))
  val wbRd = WireDefault(0.U(5.W))
  val wbWd = WireDefault(0.U(32.W))
  val wbRw = WireDefault(false.B)
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
  registerFile.io.writeRegister := wbRd // Wires from WB stage
  registerFile.io.writeData := wbWd // Wires from WB stage
  registerFile.io.regWrite := wbRw // Wires from WB stage

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
  val exMemAluOut = RegInit(0.U(32.W))
  val exMemRd = RegInit(0.U(5.W))
  val exMemRegWrite = RegInit(false.B)
  exMemAluOut := executeStage.io.out.aluOut
  exMemRd := idExReg.dest(4,0)
  exMemRegWrite := executeStage.io.out.regWrite

  
  val memStage = Module(new MemStage())
  memStage.io.in.aluOut := exMemAluOut
  memStage.io.in.addrWord := exMemAluOut(4, 2)
  memStage.io.in.storeData := exMemAluOut
  memStage.io.in.memRead := executeStage.io.out.memRead
  memStage.io.in.memWrite := executeStage.io.out.memWrite
  memStage.io.in.rd := exMemRd
  memStage.io.in.regWrite := exMemRegWrite
  memStage.io.in.memToReg := executeStage.io.out.memToReg
  
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

  val memWbWire = Wire(new MemWbIO)
  memWbWire.memData    := memWbData
  memWbWire.aluOut     := exMemAluOut        // or memStage.io.out.aluOut if you prefer
  memWbWire.wbRd       := memWbRd
  memWbWire.wbRegWrite := memWbRegWrite
  memWbWire.wbMemToReg := memWbMemToReg

  wbStage.io.in := memWbWire
  

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
  io.led := false.B

  // For debugging writeback stage
  io.id_wbEnable := decodeStage.io.out.RegWrite
  io.ex_wbEnable := executeStage.io.out.regWrite
  io.mem_wbEnable := memStage.io.out.wbRegWrite
  io.wb_wbEnable := wbStage.io.rfRegWrite

}
object StagesCombined extends App {
  emitVerilog(new AddiPipelineTop(Array(0x00000013), 0), Array("--target-dir", "generated"))
}