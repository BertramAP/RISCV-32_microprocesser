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
  val pc = UInt(32.W)
  val RegWrite = Bool()
  val ALUSrc = Bool()
  val PCSrc = Bool()
  val MemRead = Bool()
  val MemWrite = Bool()
  val MemToReg = Bool()
}

class DecodeInputIO extends Bundle {
  val instr = UInt(32.W)
  val pc    = UInt(32.W)
  
}
class ControllerExecuteIO extends Bundle {
  val RegWrite = Bool()
  val ALUSrc = Bool()
  val PCSrc = Bool()
  val MemRead = Bool()
  val MemWrite = Bool()
  val MemToReg = Bool()
}