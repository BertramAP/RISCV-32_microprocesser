package stages

import chisel3._
import chisel3.util._

class AddiPipelineTop(code: Array[Int], PcStart: Int) extends Module {
  val io = IO(new Bundle {
    // Board outputs
    val led = Output(Bool())
  })

  val fetchStage = Module(new FetchStage(code, PcStart))
  val wbRd = WireDefault(0.U(5.W))
  val wbWd = WireDefault(0.U(32.W))
  val wbRw = WireDefault(false.B)
  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  ifIdReg := fetchStage.io.out

  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in := ifIdReg
  
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

  // LED Board Hello World: Use a register to hold the LED state
  val ledReg = RegInit(false.B)
  when(wbRw && wbRd === 4.U) {
    ledReg := wbWd(0)
  }
  io.led := ledReg
}

object StagesCombined extends App {
  val CodeToBeExecuted = Array(
      0x00100213, // addi x4, x0, 1
      0x00000013, // addi x0, x0, 0 NOP 
      0x00000213, // addi x4, x0, 0
      0x00000013, // addi x0, x0, 0 NOP
      0x00000013, // addi x0, x0, 0 NOP 
      0xFE0000C3  // beq x0, x0, -16 (Loop back to start)
    )
  emitVerilog(new AddiPipelineTop(CodeToBeExecuted, 0), Array("--target-dir", "generated"))
}