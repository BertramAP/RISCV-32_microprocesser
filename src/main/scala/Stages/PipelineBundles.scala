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
  val stall = Bool()
}

class DecodeExecuteIO extends Bundle {
  val src1   = UInt(32.W)
  val src2   = UInt(32.W)
  val rs1_addr = UInt(5.W)
  val rs2_addr = UInt(5.W)
  val imm    = UInt(32.W)
  val dest   = UInt(5.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  val pc = UInt(32.W)
  val isPC = Bool()
  val isJump = Bool()
  val isJumpr = Bool()
  val isBranch = Bool()

  // Control signals for execute stage
  val aluSrc = Bool()
  val aluOp  = UInt(4.W)

  // Control signals for memory stage
  val memWrite = Bool()
  val memRead = Bool()

  // Control signals for writeback stage
  val regWrite = Bool()
  val memToReg = Bool()

  val done = Bool()
}

class DecodeOutputsIO extends Bundle {
  val aluOp  = UInt(4.W)
  val src1   = UInt(5.W)
  val src2   = UInt(5.W)
  val imm    = UInt(32.W)
  val dest   = UInt(5.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  val pc = UInt(32.W)
  val isPC = Bool()
  val regWrite = Bool()
  val aluSrc = Bool()
  val isJump = Bool()
  val isJumpr = Bool()
  val isBranch = Bool()
  val memRead = Bool()
  val memWrite = Bool()
  val memToReg = Bool()
  val done = Bool()
  
  // Hazard Detection Helpers
  val usesSrc1 = Bool()
  val usesSrc2 = Bool()
}

class ExecuteMemIO extends Bundle {
    val pc          = UInt(32.W) // Debug msg
    val aluOut      = UInt(32.W)
    val addrWord    = UInt(32.W)    // Word index
    val storeData   = UInt(32.W)
    val rd          = UInt(5.W)
    val funct3      = UInt(3.W)
    
    // Control signals for memory stage
    val memRead     = Bool()
    val memWrite    = Bool()

    // Control signals for writeback stage
    val regWrite    = Bool()
    val memToReg    = Bool()
    
    val done        = Bool()
}

class MemWbIO extends Bundle {
    val pc         = UInt(32.W) // Debug msg
    val memData    = UInt(32.W) 
    val aluOut     = UInt(32.W)
    val wbRd       = UInt(5.W)
    val funct3     = UInt(3.W)

    // Control signal for writeback stage
    val wbRegWrite = Bool()
    val wbMemToReg = Bool()
    
    val done       = Bool()
}

class DecodeInputIO extends Bundle {
  val instr = UInt(32.W)
  val pc    = UInt(32.W)
}

class ControllerExecuteIO extends Bundle {
  val regWrite = Bool()
  val aluSrc = Bool()
  val isJump = Bool()
  val isJumpr = Bool()
  val isBranch = Bool()
  val memRead = Bool()
  val memWrite = Bool()
  val memToReg = Bool()
  val done = Bool()
}