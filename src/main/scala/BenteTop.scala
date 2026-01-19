package stages

import chisel3._
import chisel3.util._

class BenteTop(imemInitArr: Array[Int], dmemInitArr: Array[Int], PcStart: Int, memSizeWords: Int = 128) extends Module {
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
    
    val debug_regFile = Output(Vec(32, UInt(32.W)))
  
    // Board outputs
    val done = Output(Bool())
    
    // Instruction memory write ports for testing
    val imemWe    = Input(Bool())
    val imemWaddr = Input(UInt(log2Ceil(memSizeWords).W))
    val imemWdata = Input(UInt(32.W))

    // Data memory write ports for testing
    val dmemWe    = Input(Bool())
    val dmemWaddr = Input(UInt(log2Ceil(memSizeWords).W))
    val dmemWdata = Input(UInt(32.W))

    val run = Input(Bool())
    val led = Output(Bool())
  })

  val imem = SyncReadMem(memSizeWords, UInt(32.W))
  
  when(io.imemWe) {
    imem.write(io.imemWaddr, io.imemWdata)
    when (io.run) {
        printf(p"IMEM WRITE EXEC: Addr=0x${Hexadecimal(io.imemWaddr)} Data=0x${Hexadecimal(io.imemWdata)}\n")
    }
  }
  // Fetch stage
  val fetchStage = Module(new FetchStage(PcStart, memSizeWords))

  val shouldStall = Wire(Bool())
  val globalStall = Wire(Bool()) // shouldStall || !io.run // Moved assignment down

  // Use SyncReadMem read with enable to maintain pipeline timing and support stalling
  fetchStage.io.imemInstr := imem.read(fetchStage.io.imemAddr, !globalStall)
  
  fetchStage.io.in.stall := globalStall
  fetchStage.io.in.branchTaken  := false.B
  fetchStage.io.in.branchTarget := 0.U

  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  val ifIdValid = RegInit(false.B) // Valid bit for synchronous memory bypass

  io.if_pc := fetchStage.io.out.pc
  io.if_instr := fetchStage.io.out.instr

  val decodeStage = Module(new DecodeStage())
  decodeStage.io.in.instr := Mux(ifIdValid, ifIdReg.instr, 0.U)
  decodeStage.io.in.pc := ifIdReg.pc
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := decodeStage.io.out.src1
  registerFile.io.readRegister2 := decodeStage.io.out.src2

  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  
  val executeStage = Module(new ExecuteStage())
  fetchStage.io.in.branchTaken := executeStage.io.BranchOut.branchTaken
  fetchStage.io.in.branchTarget := executeStage.io.BranchOut.branchTarget
  
  executeStage.io.in := idExReg
  io.ex_aluOut := executeStage.io.out.aluOut

  
  // EX/MEM pipeline registers
  val exMemReg = RegInit(0.U.asTypeOf(new ExecuteMemIO))
  exMemReg := executeStage.io.out
    
  val memStage = Module(new MemStage(dmemInitArr, memSizeWords))
  memStage.io.in := exMemReg

  // Memory write ports for testing
  memStage.io.dmemWe    := io.dmemWe
  memStage.io.dmemWaddr := io.dmemWaddr
  memStage.io.dmemWdata := io.dmemWdata
  
  // MEM/WB pipeline registers
  val memWriteBackReg = RegInit(0.U.asTypeOf(new MemWbIO))
  memWriteBackReg := memStage.io.out
  
  val writeBackStage = Module(new WritebackStage())
  writeBackStage.io.in := memWriteBackReg
  writeBackStage.io.in.memData := memStage.io.out.memData // Bypass memWriteBackReg for memData due to 1-cycle SyncReadMem latency.


  registerFile.io.writeRegister := writeBackStage.io.rfWriteRd
  registerFile.io.writeData := writeBackStage.io.rfWriteData
  registerFile.io.regWrite := writeBackStage.io.rfRegWrite
  
  io.done := writeBackStage.io.done
  fetchStage.io.in.done := writeBackStage.io.done

  
  // Hazard Detection (Load-Use -> Stall)
  // Check if instruction in EX (idExReg) is a Load and dest matches rs1 or rs2 of instruction in ID
  val idExMemRead = idExReg.memRead
  val idExRd = idExReg.dest
  
  // Also check if instruction in MEM (exMemReg) is a Load (Extended stall for SyncReadMem latency)
  val exMemRead = exMemReg.memRead
  val exMemRd   = exMemReg.rd

  val rs1 = decodeStage.io.out.src1
  val rs2 = decodeStage.io.out.src2
  val usesSrc1 = decodeStage.io.out.usesSrc1
  val usesSrc2 = decodeStage.io.out.usesSrc2
  
  val branchTaken = executeStage.io.BranchOut.branchTaken
  val branchFlush = RegNext(branchTaken, false.B)

  val stallIDEx = (idExMemRead && (idExRd =/= 0.U) && ((idExRd === rs1 && usesSrc1) || (idExRd === rs2 && usesSrc2)))
  val stallExMem = (exMemRead   && (exMemRd =/= 0.U) && ((exMemRd === rs1 && usesSrc1) || (exMemRd === rs2 && usesSrc2)))
  shouldStall := stallIDEx || stallExMem
  // If branch is taken, we flush pipeline, so we shouldn't stall for the flushed instruction
  globalStall := (shouldStall && !branchTaken) || !io.run




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



  when (branchTaken || shouldStall) {
     idExReg := 0.U.asTypeOf(new DecodeExecuteIO) // Flush
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

     idExReg.src1 := registerFile.io.readData1
     idExReg.src2 := registerFile.io.readData2
     idExReg.rs1_addr := decodeStage.io.out.src1
     idExReg.rs2_addr := decodeStage.io.out.src2

  }
  io.debug_regFile := registerFile.io.debug_registers
  // Debug outputs
  io.ifid_instr := ifIdReg.instr

  io.id_readAddress1 := registerFile.io.readRegister1
  io.id_readData1 := registerFile.io.readData1
  io.id_rd := idExReg.dest(4,0)
  io.id_imm := decodeStage.io.out.imm
  io.id_regWrite := decodeStage.io.out.regWrite

  io.ex_rd := executeStage.io.out.rd
  io.ex_regWrite := executeStage.io.out.regWrite
  io.ex_branchTaken := executeStage.io.BranchOut.branchTaken
  io.ex_branchTarget := executeStage.io.BranchOut.branchTarget

  io.mem_ALUOut := memStage.io.in.aluOut
  io.mem_rd := memStage.io.in.rd
  io.mem_regWrite := memStage.io.in.regWrite

  io.wb_wdata := writeBackStage.io.rfWriteData
  io.wb_rd := writeBackStage.io.rfWriteRd

  // For debugging writeback stage
  io.id_wbEnable := decodeStage.io.out.regWrite
  io.ex_wbEnable := executeStage.io.out.regWrite
  io.mem_wbEnable := memStage.io.out.wbRegWrite
  io.wb_wbEnable := writeBackStage.io.rfRegWrite
  io.led := registerFile.io.x1 === 1.U // Enable led by setting x1 to 1

} 
