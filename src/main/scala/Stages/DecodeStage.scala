package stages

import chisel3._
import chisel3.util._

class DecodeStage extends Module {
  // TODO: Output immediate value with its own wire.
  
  // Helper function for sign-extending I-type immediates
  def signExtendIType(instr: UInt): UInt = {
    Cat(Fill(20, instr(31)), instr(31, 20))
  }
  def signExtendBType(instr: UInt): UInt = {
    Cat(Fill(19, instr(31)), instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W))
  }
  val io = IO(new Bundle {
    // Inputs from the Fetch stage
    val in = Input(new DecodeInputIO)
    // Outputs to the Execute stage
    val out = Output(new DecodeExecuteIO)
  })
  val aluOp = WireDefault(0.U(4.W))
  val src1 = WireDefault(0.U(32.W))
  val src2 = WireDefault(0.U(32.W))
  val dest = WireDefault(0.U(32.W))
  val funct3 = WireDefault(0.U(3.W))
  val opcode = WireDefault(io.in.instr(6, 0))
  val rd = WireDefault(io.in.instr(11, 7))
  val funct7 = WireDefault(0.U(1.W))

  
  io.out.pc := io.in.pc
  val registerFile = Module(new RegisterFile())

  registerFile.io.readRegister1 := src1
  registerFile.io.readRegister2 := src2
  registerFile.io.writeRegister := 0.U
  registerFile.io.writeData := 0.U
  registerFile.io.regWrite := false.B

  val controller = Module(new Controller())
  controller.io.opcode := opcode
  io.out.RegWrite := controller.io.out.RegWrite
  io.out.ALUSrc := controller.io.out.ALUSrc
  io.out.PCSrc := controller.io.out.PCSrc
  io.out.MemRead := controller.io.out.MemRead
  io.out.MemWrite := controller.io.out.MemWrite
  io.out.MemToReg := controller.io.out.MemToReg
  val imm = WireDefault(0.U(32.W))
  
  switch(opcode) {
    is(3.U) { // Load type
      imm := io.in.instr(31, 20)
      src1 := (io.in.instr(19, 15))
      src2 := imm
      funct3 := io.in.instr(14, 12)
      dest := rd
      aluOp := ALUops.ALU_ADD // Load uses addition
    }

    is(19.U) { // I-Type
      imm := signExtendIType(io.in.instr)
      dest := rd
      funct3 := io.in.instr(14, 12)
      funct7 := imm(9)
      src1 := io.in.instr(19, 15)
      src2 := 0.U
      // Determine ALU operation based on funct3 and funct7
      switch(funct3) {
        is(0.U) { aluOp := ALUops.ALU_ADD } // ADDI 
        is(1.U) { aluOp := ALUops.ALU_SLL } // SLLI
        is(2.U) { aluOp := ALUops.ALU_SLT } // SLTI
        is(3.U) { aluOp := ALUops.ALU_SLTU } // SLTIU
        is(4.U) { aluOp := ALUops.ALU_XOR } // XORI
        is(5.U) { aluOp := Mux(funct7 === 0.U, ALUops.ALU_SRL, ALUops.ALU_SRA) } // SRLI/SRAI
        is(6.U) { aluOp := ALUops.ALU_OR } // OR
        is(7.U) { aluOp := ALUops.ALU_AND } // ANDI
      }
    }
    is (23.U) { // auipc
      // TODO: find how to share pc
      imm := Cat(io.in.instr(31, 12), Fill(12, 0.U))
      dest := rd
      src1 := 0.U
      src2 := 0.U
      aluOp := ALUops.ALU_ADD
    }
    is(35.U) { // Store type
      //WIP
      imm := Cat(io.in.instr(31, 25), io.in.instr(11, 7))
      src1 := (io.in.instr(19, 15))
      src2 := io.in.instr(24, 20)
      funct3 := io.in.instr(14, 12)
      dest := imm
      aluOp := ALUops.ALU_ADD // Store uses addition
    } 
    is(51.U) { // R-type
      imm := 0.U
      dest := rd
      src1 := io.in.instr(19, 15)
      src2 := io.in.instr(24, 20)
      funct3 := io.in.instr(14, 12)
      funct7 := io.in.instr(30)
      switch(funct3) {
        is(0.U) { aluOp := Mux(funct7 === 0.U, ALUops.ALU_ADD, ALUops.ALU_SUB) } // ADD/SUB
        is(1.U) { aluOp := ALUops.ALU_SLL } // SLL
        is(2.U) { aluOp := ALUops.ALU_SLT } // SLT
        is(3.U) { aluOp := ALUops.ALU_SLTU } // SLTU
        is(4.U) { aluOp := ALUops.ALU_XOR } // XOR
        is(5.U) { aluOp := Mux(funct7 === 0.U, ALUops.ALU_SRL, ALUops.ALU_SRA) } // SRL/SRA
        is(6.U) { aluOp := ALUops.ALU_OR } // OR
        is(7.U) { aluOp := ALUops.ALU_AND } // AND
      }
    } 
    is(55.U) { // LUI
      imm := Cat(io.in.instr(31, 12), Fill(12, 0.U))
      dest := rd
      src1 := 0.U
      src2 := 0.U // Maybe set it to 12
      aluOp := ALUops.ALU_ADD // LUI uses addition with 0
    }
    is(99.U) { // Branch type
      //Double check if works, and sign extension
      imm := signExtendBType(io.in.instr)
      funct3 := io.in.instr(14, 12)
      dest := 0.U // Branches do not write to a destination register
      src1 := io.in.instr(19, 15)
      src2 := io.in.instr(24, 20)
      aluOp := ALUops.ALU_ADD // Branches use addition to calculate target address
    }
    is(111.U) { // JAL
      imm := Cat(Fill(19, io.in.instr(31)), io.in.instr(31), io.in.instr(19, 12), io.in.instr(20), io.in.instr(30, 21)) // Sign extended
      src1 := 0.U
      src2 := 0.U
      aluOp := ALUops.ALU_ADD // JAL uses addition to calculate target address
    }
    is(103.U) { // JALR
      imm := signExtendIType(io.in.instr)
      dest := rd
      src1 := io.in.instr(19, 15)
      src2 := 0.U
      aluOp := ALUops.ALU_ADD // JALR uses addition to calculate target address
    }
    is(115.U) { // ECALL/EBREAK
      // TODO: handle system instructions
    }
  }

  io.out.src2 := registerFile.io.readData2
  io.out.imm := imm
  io.out.aluOp := aluOp // Temporary, to be set based on instruction decoding
  io.out.src1 := registerFile.io.readData1
  io.out.dest := dest
  io.out.funct3 := funct3
  io.out.funct7 := funct7
}
/*
object DecodeStage extends App {
  emitVerilog(new DecodeStage(), Array("--target-dir", "generated"))
}*/