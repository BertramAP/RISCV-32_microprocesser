package stages

import chisel3._

class FetchDecodeIO extends Bundle {
  val instr = UInt(32.W)
  val pc    = UInt(32.W)
}

class FetchBranchIO extends Bundle {
  val branchTaken = Bool()
  val branchTarget = UInt(32.W)
  val done  = Bool()
}

class DecodeExecuteIO extends Bundle {
  val src1   = UInt(32.W)
  val src2   = UInt(32.W)
  val imm    = UInt(32.W)
  val dest   = UInt(5.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(1.W)
  val pc = UInt(32.W)
  val isPC = Bool()
  val isJump = Bool()
  val isJumpr = Bool()
  val isBranch = Bool()

  // Control signals for execute stage
  val ALUSrc = Bool()
  val aluOp  = UInt(4.W)

  // Control signals for memory stage
  val MemWrite = Bool()
  val MemRead = Bool()

  // Control signals for writeback stage
  val RegWrite = Bool()
  val MemToReg = Bool()

  val done = Bool()
}

class DecodeOutputsIO extends Bundle {
  val aluOp  = UInt(4.W)
  val src1   = UInt(5.W)
  val src2   = UInt(5.W)
  val imm    = UInt(32.W)
  val dest   = UInt(5.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(1.W)
  val pc = UInt(32.W)
  val isPC = Bool()
  val RegWrite = Bool()
  val ALUSrc = Bool()
  val isJump = Bool()
  val isJumpr = Bool()
  val isBranch = Bool()
  val MemRead = Bool()
  val MemWrite = Bool()
  val MemToReg = Bool()
  val done = Bool()
}

class ExecuteMemIO extends Bundle {
    val aluOut      = UInt(32.W)
    val addrWord    = UInt(3.W)    // 0..7 (word index)
    val storeData   = UInt(32.W)
    val rd          = UInt(5.W)
    
    // Control signals for memory stage
    val memRead     = Bool()
    val memWrite    = Bool()

    // Control signals for writeback stage
    val regWrite    = Bool()
    val memToReg    = Bool()
}

class MemWbIO extends Bundle {
    val memData    = UInt(32.W)
    val aluOut     = UInt(32.W)
    val wbRd       = UInt(5.W)

    // Control signal for writeback stage
    val wbRegWrite = Bool()
    val wbMemToReg = Bool()
}

class DecodeInputIO extends Bundle {
  val instr = UInt(32.W)
  val pc    = UInt(32.W)
}

class ControllerExecuteIO extends Bundle {
  val RegWrite = Bool()
  val ALUSrc = Bool()
  val isJump = Bool()
  val isJumpr = Bool()
  val isBranch = Bool()
  val MemRead = Bool()
  val MemWrite = Bool()
  val MemToReg = Bool()
  val done = Bool()
}