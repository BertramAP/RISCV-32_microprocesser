package stages

import chisel3._

class FetchDecodeIO extends Bundle {
  val instr = UInt(32.W)
  val pc    = UInt(32.W)
}

class DecodeExecuteIO extends Bundle {
  val aluOp  = UInt(4.W)
  val src1   = UInt(32.W)
  val src2   = UInt(32.W)
  val dest   = UInt(32.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(1.W)
}

class ExecuteMemIO extends Bundle {
    val aluOut      = UInt(32.W)
    val addrWord    = UInt(3.W)    // 0..7 (word index)
    val storeData   = UInt(32.W)
    val memRead     = Bool()
    val memWrite    = Bool()

    val rd          = UInt(5.W)
    val regWrite    = Bool()
    val memToReg    = Bool()
}

class MemWbIO extends Bundle {
    val memData    = UInt(32.W)
    val aluOut     = UInt(32.W) 
    val wbRd       = UInt(5.W)
    val wbRegWrite = Bool()
    val wbMemToReg = Bool()
}