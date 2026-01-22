package stages

import chisel3._
import chisel3.util._

class BenteTop(imemInitArr: Array[Int], dmemInitArr: Array[Int], PcStart: Int, memSizeWords: Int = 4096) extends Module {
  val io = IO(new Bundle {
    
    // For debugging with uart
    val debugRegVal = Output(UInt(32.W))
    // For InstructionTest
    val debug_regFile = Output(Vec(32, UInt(32.W)))
  
    // Board outputs
    val done = Output(Bool())
    
    // Instruction memory write ports for InstructionTest
    val imemWe    = Input(Bool())
    val imemWaddr = Input(UInt(log2Ceil(memSizeWords).W))
    val imemWdata = Input(UInt(32.W))

    // Data memory write ports for InstructionTest
    val dmemWe    = Input(Bool())
    val dmemWaddr = Input(UInt(log2Ceil(memSizeWords).W))
    val dmemWdata = Input(UInt(32.W))

    val run = Input(Bool())
  })

  val imem = SyncReadMem(memSizeWords, UInt(32.W))
  // Instruction memory write ports for InstructionTest
  when(io.imemWe) {
    imem.write(io.imemWaddr, io.imemWdata)
  }
  // Fetch stage
  val fetchStage = Module(new FetchStage(PcStart, memSizeWords))

  val shouldStall = Wire(Bool())
  val globalStall = Wire(Bool())

  fetchStage.io.imemInstr := imem.read(fetchStage.io.imemAddr, !globalStall)
  
  fetchStage.io.in.stall := globalStall
  fetchStage.io.in.branchTaken  := false.B
  fetchStage.io.in.branchTarget := 0.U

  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  val ifIdValid = RegInit(false.B)


  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in.instr := Mux(ifIdValid, ifIdReg.instr, 0.U)
  decodeStage.io.in.pc := ifIdReg.pc
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := decodeStage.io.out.src1
  registerFile.io.readRegister2 := decodeStage.io.out.src2

  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  
  val executeStage = Module(new ExecuteStage(memSizeWords))
  fetchStage.io.in.branchTaken := executeStage.io.BranchOut.branchTaken
  fetchStage.io.in.branchTarget := executeStage.io.BranchOut.branchTarget
  
  executeStage.io.in := idExReg

  // EX/MEM pipeline registers
  val exMemReg = RegInit(0.U.asTypeOf(new ExecuteMemIO(memSizeWords)))
  exMemReg := executeStage.io.out

  val memStage = Module(new MemStage(dmemInitArr, memSizeWords))
  memStage.io.in := exMemReg

  // Memory write ports for InstructionTest
  memStage.io.dmemWe    := io.dmemWe
  memStage.io.dmemWaddr := io.dmemWaddr
  memStage.io.dmemWdata := io.dmemWdata

  // MEM/WB pipeline registers
  val memWriteBackReg = RegInit(0.U.asTypeOf(new MemWbIO))
  memWriteBackReg := memStage.io.out
  
  val writeBackStage = Module(new WritebackStage())
  writeBackStage.io.in := memWriteBackReg
  writeBackStage.io.in.memData := memStage.io.out.memData // Bypass memWriteBackReg for memData due to 1-cycle SyncReadMem latency.

 // Register file writeback
  registerFile.io.writeRegister := writeBackStage.io.rfWriteRd
  registerFile.io.writeData := writeBackStage.io.rfWriteData
  registerFile.io.regWrite := writeBackStage.io.rfRegWrite
  
  io.done := writeBackStage.io.done  
  fetchStage.io.in.done := writeBackStage.io.done

  
  // Hazard Detection (Load-Use -> Stall)
  // Check if instruction in EX (idExReg) is a Load and dest matches rs1 or rs2 of instruction in ID
  val idExMemRead = idExReg.memRead
  val idExRd = idExReg.dest
  
  val rs1 = decodeStage.io.out.src1
  val rs2 = decodeStage.io.out.src2
  val usesSrc1 = decodeStage.io.out.usesSrc1
  val usesSrc2 = decodeStage.io.out.usesSrc2
  
  val branchTaken = executeStage.io.BranchOut.branchTaken

  val stallIDEx = (idExMemRead && (idExRd =/= 0.U) && ((idExRd === rs1 && usesSrc1) || (idExRd === rs2 && usesSrc2)))
  shouldStall := stallIDEx
  // If branch is taken, we flush pipeline, so we shouldn't stall for the flushed instruction
  globalStall := (shouldStall) || !io.run
  val branchFlush = RegNext(branchTaken, false.B)


  // IF/ID Update Logic
  when (branchTaken || branchFlush) {
    ifIdReg := 0.U.asTypeOf(new FetchDecodeIO) // Flush
    ifIdValid := false.B
  } .elsewhen (!globalStall) {
    ifIdReg := fetchStage.io.out
    ifIdValid := true.B
  } .otherwise {
    // Stall: keep current value
    ifIdReg := ifIdReg
    ifIdValid := ifIdValid
  }

  // ID/EX Update Logic & Forwarding
  // Forwarding Logic now in ExecuteStage
  executeStage.io.IO_forwarding.mem_rd := memStage.io.out.wbRd 
  executeStage.io.IO_forwarding.mem_regWrite := memStage.io.out.wbRegWrite

  executeStage.io.IO_forwarding.mem_aluOut := memStage.io.out.aluOut
  
  executeStage.io.IO_forwarding.wb_rd := writeBackStage.io.rfWriteRd
  executeStage.io.IO_forwarding.wb_regWrite := writeBackStage.io.rfRegWrite
  executeStage.io.IO_forwarding.wb_writeData := writeBackStage.io.rfWriteData

// 1. Default: Always load the next instruction from Decode.
  // This connects the heavy data buses (src1, src2, imm) directly, 
  // without 'branchTaken' interfering in their path.
  idExReg.imm      := decodeStage.io.out.imm
  idExReg.dest     := decodeStage.io.out.dest
  idExReg.funct3   := decodeStage.io.out.funct3
  idExReg.funct7   := decodeStage.io.out.funct7
  idExReg.pc       := decodeStage.io.out.pc
  idExReg.isPC     := decodeStage.io.out.isPC
  idExReg.aluSrc   := decodeStage.io.out.aluSrc
  idExReg.aluOp    := decodeStage.io.out.aluOp
  idExReg.memToReg := decodeStage.io.out.memToReg
  
  idExReg.src1     := registerFile.io.readData1
  idExReg.src2     := registerFile.io.readData2
  idExReg.rs1_addr := decodeStage.io.out.src1
  idExReg.rs2_addr := decodeStage.io.out.src2

  // 2. Control Signals: Load normally, OR kill them if we need to flush/stall.
  // 'branchTaken' only loads these few bits, reducing fanout from ~150 to ~10.
  when (branchTaken || shouldStall) {
     idExReg.regWrite := false.B
     idExReg.memWrite := false.B
     idExReg.memRead  := false.B
     idExReg.isBranch := false.B
     idExReg.isJump   := false.B
     idExReg.isJumpr  := false.B
     idExReg.done     := false.B
  } .otherwise {
     idExReg.regWrite := decodeStage.io.out.regWrite
     idExReg.memWrite := decodeStage.io.out.memWrite
     idExReg.memRead  := decodeStage.io.out.memRead
     idExReg.isBranch := decodeStage.io.out.isBranch
     idExReg.isJump   := decodeStage.io.out.isJump
     idExReg.isJumpr  := decodeStage.io.out.isJumpr
     idExReg.done     := decodeStage.io.out.done
  }
  io.debugRegVal := registerFile.io.debugRegVal
  io.debug_regFile := registerFile.io.debug_regFile
}
