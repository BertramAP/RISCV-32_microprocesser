package Stages

import chisel3._
import chisel3.util._

class DecodeStage extends Module {

  // Helper function for sign-extending I-type immediates
  def signExtendIType(instr: UInt): UInt = {
    Cat(Fill(20, instr(31)), instr(31, 20))
  }
  val io = IO(new Bundle {
    // Inputs from the Fetch stage
    val instr = Input(UInt(32.W))
    val pc = Input(UInt(32.W))
    // Outputs to the Execute stage
    val aluOp = Output(UInt(4.W))
    val src1 = Output(UInt(32.W))
    val src2 = Output(UInt(32.W))
    val dest = Output(UInt(32.W))
    val funct3 = Output(UInt(3.W))
    val funct7 = Output(UInt(1.W))
  })
  val aluOp = WireDefault(0.U(4.W))
  val src1 = WireDefault(0.U(32.W))
  val src2 = WireDefault(0.U(32.W))
  val dest = WireDefault(0.U(32.W))
  val funct3 = WireDefault(0.U(3.W))
  val opcode = WireDefault(0.U(7.W))
  opcode := io.instr(6, 0)
  val rd = WireDefault(0.U(5.W))
  rd := io.instr(11, 7)
  val funct7 = WireDefault(0.U(1.W)) // We only care about bit 30, since func 7 can only be 0x00 or 0x20
  val isImm = Wire(Bool())
  isImm := false.B
  funct7 := 0.U

  val registerFile = Module(new RegisterFile())

  registerFile.io.readRegister1 := src1
  registerFile.io.readRegister2 := src2
  registerFile.io.writeRegister := 0.U
  registerFile.io.writeData := 0.U
  registerFile.io.regWrite := false.B

  switch(opcode) {
    is(19.U) { // I-Type
      isImm := true.B
      val imm = io.instr(31, 20)
      dest := rd
      funct3 := io.instr(14, 12)
      when(funct3 === 0x1.U || funct3 === 0x5.U) {
        src2 := imm
        // TODO: handle funct7 for slli and srli
        funct7 := io.instr(30)
      }.otherwise {
        src2 := imm
      }
    } 
    is(51.U) { // R-type
      dest := rd
      src1 := io.instr(19, 15)
      src2 := io.instr(24, 20)
      funct3 := io.instr(14, 12)
      funct7 := io.instr(30)
    } 
    is(3.U) { // Load type
      isImm := true.B
      val imm = io.instr(31, 20)
      src1 := (io.instr(19, 15))
      src2 := imm
      funct3 := io.instr(14, 12)
      dest := rd
    } 
    is(35.U) { // Store type
      val imm = Cat(io.instr(31, 25), io.instr(11, 7))
      isImm := true.B
      src1 := (io.instr(19, 15))
      src2 := imm
      funct3 := io.instr(14, 12)
    } 
  }

  when(isImm) {
    io.src2 := src2
  }.otherwise {
    io.src2 := registerFile.io.readData2
  }

  io.aluOp := aluOp // Temporary, to be set based on instruction decoding
  io.src1 := registerFile.io.readData1
  io.dest := dest
  io.funct3 := funct3
  io.funct7 := funct7
}

object DecodeStage extends App {
  emitVerilog(new DecodeStage(), Array("--target-dir", "generated"))
  }