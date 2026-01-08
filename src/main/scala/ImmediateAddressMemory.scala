package stages

import chisel3._
import chisel3.util._

class ImmediateAddressMemoryTop extends Module {
  val io = IO(new Bundle {
    val instr      = Input(UInt(32.W))
    val storeValue = Input(UInt(32.W))

    val opcode     = Output(UInt(7.W))
    val immI       = Output(UInt(32.W))
    val immS       = Output(UInt(32.W))
    val immUsed    = Output(UInt(32.W))
    val aluOut     = Output(UInt(32.W))
    val addrWord   = Output(UInt(3.W))

    val memRead    = Output(Bool())
    val memWrite   = Output(Bool())
    val memData    = Output(UInt(32.W))

    val dbgMem     = Output(Vec(8, UInt(32.W)))
  })

  val opcode = io.instr(6,0)
  io.opcode := opcode

  val isLoad  = opcode === "b0000011".U
  val isStore = opcode === "b0100011".U
  val isIType = opcode === "b0010011".U

  val immI = Cat(Fill(20, io.instr(31)), io.instr(31,20))
  val immS = Cat(Fill(20, io.instr(31)), io.instr(31,25), io.instr(11,7))

  io.immI := immI
  io.immS := immS

  val immUsed = Mux(isStore, immS, immI)
  io.immUsed := immUsed

  val aluOut = 0.U(32.W) + immUsed
  io.aluOut := aluOut
  io.addrWord := aluOut(4,2)

  val exmem = Wire(new ExecuteMemIO)
  exmem.aluOut    := aluOut
  exmem.addrWord  := aluOut(4,2)
  exmem.storeData := io.storeValue
  exmem.memRead   := isLoad
  exmem.memWrite  := isStore
  exmem.rd        := io.instr(11,7)
  exmem.regWrite  := isLoad || isIType
  exmem.memToReg  := isLoad

  io.memRead  := exmem.memRead
  io.memWrite := exmem.memWrite

  val mem = Module(new MemStage(8))
  mem.io.in := exmem
  val wbStage = Module(new WritebackStage())
  wbStage.io.in := mem.io.out

  val rf = Module(new RegisterFile())
  rf.io.readRegister1 := 0.U
  rf.io.readRegister2 := 0.U
  rf.io.writeRegister := wbStage.io.rfWriteRd
  rf.io.writeData     := wbStage.io.rfWriteData
  rf.io.regWrite      := wbStage.io.rfRegWrite
  

  io.memData := mem.io.out.memData
  io.dbgMem  := mem.io.dbgMem  
}
