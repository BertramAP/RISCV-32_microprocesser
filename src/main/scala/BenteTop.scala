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

  val shouldStall = Wire(Bool())
  fetchStage.io.in.stall := shouldStall

  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))

  io.if_pc := fetchStage.io.out.pc
  io.if_instr := fetchStage.io.out.instr

  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in := ifIdReg
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := decodeStage.io.out.src1
  registerFile.io.readRegister2 := decodeStage.io.out.src2

  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  
  val executeStage = Module(new ExecuteStage())
  fetchStage.io.in <> executeStage.io.BranchOut
  executeStage.io.in := idExReg
  io.ex_aluOut := executeStage.io.out.aluOut

  
  // EX/MEM pipeline registers
  val exMemReg = RegInit(0.U.asTypeOf(new ExecuteMemIO))
  exMemReg := executeStage.io.out
    
  val memStage = Module(new MemStage(code, 4096))
  memStage.io.in := exMemReg
  
  // MEM/WB pipeline registers
  val memWriteBackReg = RegInit(0.U.asTypeOf(new MemWbIO))
  memWriteBackReg := memStage.io.out
  
  val writeBackStage = Module(new WritebackStage())
  writeBackStage.io.in := memWriteBackReg

  registerFile.io.writeRegister := writeBackStage.io.rfWriteRd
  registerFile.io.writeData := writeBackStage.io.rfWriteData
  registerFile.io.regWrite := writeBackStage.io.rfRegWrite
  
  done := memWriteBackReg.done
  
  // Hazard Detection (Load-Use -> Stall)
  // Check if instruction in EX (idExReg) is a Load and dest matches rs1 or rs2 of instruction in ID
  val idExMemRead = idExReg.memRead
  val idExRd = idExReg.dest
  val rs1 = decodeStage.io.out.src1
  val rs2 = decodeStage.io.out.src2

  shouldStall := idExMemRead && (idExRd =/= 0.U) && (idExRd === rs1 || idExRd === rs2)
  
  val branchTaken = executeStage.io.BranchOut.branchTaken

  // IF/ID Update Logic
  when (branchTaken) {
    ifIdReg := 0.U.asTypeOf(new FetchDecodeIO) // Flush
  } .elsewhen (!shouldStall) {
    ifIdReg := fetchStage.io.out
  } .otherwise {
    // Stall: keep current value
    ifIdReg := ifIdReg
  }

  // ID/EX Update Logic & Forwarding
  
  // Forwarding Sources
  // ForwardA
  val forwardA_EX = (idExReg.regWrite && idExReg.dest =/= 0.U && idExReg.dest === rs1)
  val forwardA_MEM = (exMemReg.regWrite && exMemReg.rd =/= 0.U && exMemReg.rd === rs1)
  
  // Data from EX stage
  val dataFromEX = executeStage.io.out.aluOut 
  
  // Data from MEM stage
  val dataFromMEM = Mux(memStage.io.out.wbMemToReg, memStage.io.out.memData, memStage.io.out.aluOut)

  val src1Data = Mux(forwardA_EX, dataFromEX,
      Mux(forwardA_MEM, dataFromMEM,
         Mux(decodeStage.io.out.isPC, decodeStage.io.out.pc, registerFile.io.readData1)
      )
  )

  // ForwardB
  val forwardB_EX = (idExReg.regWrite && idExReg.dest =/= 0.U && idExReg.dest === rs2)
  val forwardB_MEM = (exMemReg.regWrite && exMemReg.rd =/= 0.U && exMemReg.rd === rs2)

  val src2Data = Mux(forwardB_EX, dataFromEX,
      Mux(forwardB_MEM, dataFromMEM,
         registerFile.io.readData2
      )
  )

  when (branchTaken || shouldStall) {
     idExReg := 0.U.asTypeOf(new DecodeExecuteIO) // Flush / Bubble
  } .otherwise {
     idExReg.imm      := decodeStage.io.out.imm
     idExReg.dest     := decodeStage.io.out.dest
     idExReg.funct3   := decodeStage.io.out.funct3
     idExReg.funct7   := decodeStage.io.out.funct7
     idExReg.pc       := decodeStage.io.out.pc
     idExReg.isPC     := decodeStage.io.out.isPC
     idExReg.isJump   := decodeStage.io.out.isJump
     idExReg.isJumpr  := decodeStage.io.out.isJumpr
     idExReg.isBranch := decodeStage.io.out.isBranch
     idExReg.aluSrc   := decodeStage.io.out.aluSrc
     idExReg.aluOp    := decodeStage.io.out.aluOp
     idExReg.memWrite := decodeStage.io.out.memWrite
     idExReg.memRead  := decodeStage.io.out.memRead
     idExReg.regWrite := decodeStage.io.out.regWrite
     idExReg.memToReg := decodeStage.io.out.memToReg
     idExReg.done     := decodeStage.io.out.done

     idExReg.src1 := src1Data
     idExReg.src2 := src2Data
  }

  // Debug outputs
  io.uartTx := false.B // Not implemented

  io.ifid_instr := ifIdReg.instr

  io.id_readAddress1 := registerFile.io.readRegister1
  io.id_readData1 := registerFile.io.readData1
  io.id_rd := idExReg.dest(4,0)
  io.id_imm := decodeStage.io.out.imm
  io.id_regWrite := decodeStage.io.out.regWrite
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
  io.led := io.debug_regFile(1) === 1.U // Enable led by setting x1 to 1

  // For debugging writeback stage
  io.id_wbEnable := decodeStage.io.out.regWrite
  io.ex_wbEnable := executeStage.io.out.regWrite
  io.mem_wbEnable := memStage.io.out.wbRegWrite
  io.wb_wbEnable := writeBackStage.io.rfRegWrite
  
}
object StagesCombined extends App {
  // We do 100_000_000 clock cycles per second
  val program = Array(
    0x00000093, // addi x1 x0 0     | Init LED to off
    0x00000113, // addi x2 x0 0     | Set counter to 0
    0x007f31b7, // lui x3 2035      | Set counter target to 50M/6 = 8333333
    0x81518193, // addi x3 x3 -2027 | Set counter target to 50M/6 = 8333333
    0x00000013, // nop
    0x00000013, // nop

    // offloop:
    0x00110113, // addi x2 x2 1     | Increment counter
    0x00000013, // nop
    0x00000013, // nop
    0xfe311ae3, // bne x2 x3 -12    | Branch to offloop if we haven't reached target
    0x00000013, // nop
    0x00000013, // nop
    0x00100093, // addi x1 x0 1     | Turn LED ON

    // onloop:
    0xfff10113, // addi x2 x2 -1    | Decrement counter
    0x00000013, // nop
    0x00000013, // nop
    0xfe011ae3, // bne x2 x0 -12    | Branch to onloop if counter hasn't reached 0 yet
    0x00000013, // nop
    0x00000013, // nop
    0x00000093, // addi x1 x0 0     | Turn LED off
    0xfc0104e3, // beq x2 x0 -56    | Branch to offloop
    0x00000013, // nop
    0x00000013, // nop
  )

  emitVerilog(new BenteTop(program, 0), Array("--target-dir", "generated"))
}