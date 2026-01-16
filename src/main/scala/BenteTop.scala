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
  // Instruction memory
  val imem = SyncReadMem(memSizeWords, UInt(32.W))
  
  when(io.imemWe) {
    imem(io.imemWaddr) := io.imemWdata
  }
  // Fetch stage
  val fetchStage = Module(new FetchStage(PcStart, memSizeWords))
  fetchStage.io.imemInstr := imem(fetchStage.io.imemAddr)


  val doneWire = WireDefault(false.B)
  val shouldStall = Wire(Bool())
  val globalStall = shouldStall || !io.run
  fetchStage.io.in.done := doneWire
  fetchStage.io.in.stall := globalStall
  fetchStage.io.in.branchTaken  := false.B
  fetchStage.io.in.branchTarget := 0.U
  io.done := doneWire

 


  // IF/ID pipeline register
  val ifIdReg = RegInit(0.U.asTypeOf(new FetchDecodeIO))
  val ifIdValid = RegInit(false.B) // Valid bit for synchronous memory bypass

  io.if_pc := fetchStage.io.out.pc
  io.if_instr := fetchStage.io.out.instr

  val decodeStage = Module(new DecodeStage())
  // Bypass ifIdReg for instruction due to 1-cycle latency
  decodeStage.io.in.instr := Mux(ifIdValid, fetchStage.io.imemInstr, 0.U)
  decodeStage.io.in.pc := ifIdReg.pc
  // DecodeStage input now handled field-by-field, remove full bundle assignment or ensure it works? 
  // partial assignment to io.in works in chisel if fields are covered. 
  // But io.in := ifIdReg assigns ALL fields. 
  // Chisel "last assignment wins". So if I do io.in := ifIdReg then override io.in.instr, it should work.
  // But explicit field assignment is clearer.
  // decodeStage.io.in := ifIdReg // REMOVED
  
  val registerFile = Module(new RegisterFile())
  registerFile.io.readRegister1 := decodeStage.io.out.src1
  registerFile.io.readRegister2 := decodeStage.io.out.src2

  // ID/EX pipeline register
  val idExReg = RegInit(0.U.asTypeOf(new DecodeExecuteIO))
  
  val executeStage = Module(new ExecuteStage())
  // fetchStage.io.in <> executeStage.io.BranchOut // Removed to prevent overwriting stall
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
  // Bypass memWriteBackReg for memData due to 1-cycle latency
  writeBackStage.io.in.memData := memStage.io.out.memData

  registerFile.io.writeRegister := writeBackStage.io.rfWriteRd
  registerFile.io.writeData := writeBackStage.io.rfWriteData
  registerFile.io.regWrite := writeBackStage.io.rfRegWrite
  
  doneWire := memWriteBackReg.done
  io.done := doneWire
  fetchStage.io.in.done := writeBackStage.io.done
  io.done := writeBackStage.io.done
  
  // Hazard Detection (Load-Use -> Stall)
  // Check if instruction in EX (idExReg) is a Load and dest matches rs1 or rs2 of instruction in ID
  val idExMemRead = idExReg.memRead
  val idExRd = idExReg.dest
  val rs1 = decodeStage.io.out.src1
  val rs2 = decodeStage.io.out.src2

  shouldStall := idExMemRead && (idExRd =/= 0.U) && (idExRd === rs1 || idExRd === rs2)
  // fetchStage.io.in.stall := shouldStall // Overwritten by line 71 with globalStall


  val branchTaken = executeStage.io.BranchOut.branchTaken

  // IF/ID Update Logic
  when (branchTaken) {
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
  
  // Forwarding Sources
  // ForwardA for rs1 
  val forwardA_EX = (idExReg.regWrite && idExReg.dest =/= 0.U && idExReg.dest === rs1)
  val forwardA_MEM = (exMemReg.regWrite && exMemReg.rd =/= 0.U && exMemReg.rd === rs1)
  val forwardA_WB = (memWriteBackReg.wbRegWrite && memWriteBackReg.wbRd =/= 0.U && memWriteBackReg.wbRd === rs1)  
  
  // ForwardB for rs2
  val forwardB_EX = (idExReg.regWrite && idExReg.dest =/= 0.U && idExReg.dest === rs2)
  val forwardB_MEM = (exMemReg.regWrite && exMemReg.rd =/= 0.U && exMemReg.rd === rs2)
  val forwardB_WB = (memWriteBackReg.wbRegWrite && memWriteBackReg.wbRd =/= 0.U && memWriteBackReg.wbRd === rs2)

  val dataFromEX = executeStage.io.out.aluOut 

  val dataFromMEM = Mux(memStage.io.out.wbMemToReg, memStage.io.out.memData, memStage.io.out.aluOut)

  val dataFromWB = writeBackStage.io.rfWriteData

  val src1Data = Mux(forwardA_EX, dataFromEX,
      Mux(forwardA_MEM, dataFromMEM,
        Mux(forwardA_WB, dataFromWB, 
         Mux(decodeStage.io.out.isPC, decodeStage.io.out.pc, registerFile.io.readData1)
        )
      )
  )

  val src2Data = Mux(forwardB_EX, dataFromEX,
      Mux(forwardB_MEM, dataFromMEM,
        Mux(forwardB_WB, dataFromWB, 
         registerFile.io.readData2
        )
      )
  )

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

     idExReg.src1 := src1Data
     idExReg.src2 := src2Data
  }

  // Debug outputs


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

  // For debugging writeback stage
  io.id_wbEnable := decodeStage.io.out.regWrite
  io.ex_wbEnable := executeStage.io.out.regWrite
  io.mem_wbEnable := memStage.io.out.wbRegWrite
  io.wb_wbEnable := writeBackStage.io.rfRegWrite
  
  io.led := registerFile.io.x1 === 1.U // Enable led by setting x1 to 1
} /*
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

  emitVerilog(new BenteTop(program, program, 0), Array("--target-dir", "generated"))
}*/