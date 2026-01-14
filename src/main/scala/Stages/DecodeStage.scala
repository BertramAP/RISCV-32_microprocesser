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
    val out = Output(new DecodeOutputsIO)
  })
  val aluOp = WireDefault(0.U(4.W))
  val src1 = WireDefault(0.U(5.W))
  val src2 = WireDefault(0.U(5.W))
  val dest = WireDefault(io.in.instr(11, 7))
  val funct3 = WireDefault(0.U(3.W))
  val opcode = WireDefault(io.in.instr(6, 0))
  val funct7 = WireDefault(0.U(7.W))

  
  io.out.pc := io.in.pc

  // Default control signals
  io.out.regWrite := false.B
  io.out.aluSrc := false.B
  io.out.memRead := false.B
  io.out.memWrite := false.B
  io.out.memToReg := false.B
  io.out.isBranch := false.B
  io.out.isJump := false.B
  io.out.isJumpr := false.B
  io.out.done := false.B

  val imm = WireDefault(0.U(32.W))

  switch(opcode) {
    is(3.U) { // Load type
      io.out.regWrite := true.B
      io.out.aluSrc := true.B
      io.out.memRead := true.B
      io.out.memToReg := true.B

      imm := signExtendIType(io.in.instr)
      src1 := (io.in.instr(19, 15))
      src2 := 0.U
      funct3 := io.in.instr(14, 12)
      aluOp := ALUops.ALU_ADD // Load uses addition
    }

    is(19.U) { // I-Type
      io.out.regWrite := true.B
      io.out.aluSrc := true.B

      imm := signExtendIType(io.in.instr)
      funct3 := io.in.instr(14, 12)
      funct7 := io.in.instr(31, 25) // Use full funct7
      src1 := io.in.instr(19, 15)
      src2 := 0.U
      switch(funct3) {
        is(0.U) { aluOp := ALUops.ALU_ADD } // ADDI 
        is(1.U) { aluOp := ALUops.ALU_SLL } // SLLI
        is(2.U) { aluOp := ALUops.ALU_SLT } // SLTI
        is(3.U) { aluOp := ALUops.ALU_SLTU } // SLTIU
        is(4.U) { aluOp := ALUops.ALU_XOR } // XORI
        is(5.U) { aluOp := Mux(funct7(5) === 0.U, ALUops.ALU_SRL, ALUops.ALU_SRA) } // SRLI/SRAI
        is(6.U) { aluOp := ALUops.ALU_OR } // OR
        is(7.U) { aluOp := ALUops.ALU_AND } // ANDI
      }
    }
    is (23.U) { // auipc
      io.out.regWrite := true.B
      io.out.aluSrc := true.B

      // TODO: find how to share pc
      imm := Cat(io.in.instr(31, 12), Fill(12, 0.U))
      src1 := 0.U
      src2 := 0.U
      aluOp := ALUops.ALU_ADD
    }
    is(35.U) { // Store type
      io.out.aluSrc := true.B
      io.out.memWrite := true.B

      //WIP
      imm := Cat(Fill(20, io.in.instr(31)), io.in.instr(31, 25), io.in.instr(11, 7))
      src1 := (io.in.instr(19, 15))
      src2 := io.in.instr(24, 20)
      funct3 := io.in.instr(14, 12)
      dest := imm
      aluOp := ALUops.ALU_ADD // Store uses addition
    } 
    is(51.U) { // R-type
      io.out.regWrite := true.B

      imm := 0.U
      src1 := io.in.instr(19, 15)
      src2 := io.in.instr(24, 20)
      funct3 := io.in.instr(14, 12)
      funct7 := io.in.instr(31, 25)
      switch(funct3) {
        is(0.U) { aluOp := Mux(funct7(5) === 0.U, ALUops.ALU_ADD, ALUops.ALU_SUB) } // ADD/SUB
        is(1.U) { aluOp := ALUops.ALU_SLL } // SLL
        is(2.U) { aluOp := ALUops.ALU_SLT } // SLT
        is(3.U) { aluOp := ALUops.ALU_SLTU } // SLTU
        is(4.U) { aluOp := ALUops.ALU_XOR } // XOR
        is(5.U) { aluOp := Mux(funct7(5) === 0.U, ALUops.ALU_SRL, ALUops.ALU_SRA) } // SRL/SRA
        is(6.U) { aluOp := ALUops.ALU_OR } // OR
        is(7.U) { aluOp := ALUops.ALU_AND } // AND
      }
    } 
    is(55.U) { // LUI
      io.out.regWrite := true.B
      io.out.aluSrc := true.B

      imm := Cat(io.in.instr(31, 12), Fill(12, 0.U))
      src1 := 0.U
      src2 := 0.U // Maybe set it to 12
      aluOp := ALUops.ALU_ADD // LUI uses addition with 0
    }
    is(99.U) { // Branch type
      io.out.isBranch := true.B

      //Double check if works, and sign extension
      imm := signExtendBType(io.in.instr)
      funct3 := io.in.instr(14, 12)
      dest := 0.U // Branches do not write to a destination register
      src1 := io.in.instr(19, 15)
      src2 := io.in.instr(24, 20)
      aluOp := ALUops.ALU_SUB // Branches use subtraction for comparison
    }
    is(111.U) { // JAL
      io.out.regWrite := true.B
      io.out.isJump := true.B

      imm := Cat(
        Fill(11, io.in.instr(31)),
        io.in.instr(31),
        io.in.instr(19, 12),
        io.in.instr(20),
        io.in.instr(30, 21),
        0.U(1.W)
      )
      src1 := 0.U // PC is to large for source register, handled by mux below
      src2 := 0.U // We have an adder for adding 4 in execute stage
      aluOp := ALUops.ALU_ADD // JAL uses addition to calculate target address
    }
    is(103.U) { // JALR
      io.out.regWrite := true.B
      io.out.isJumpr := true.B

      imm := signExtendIType(io.in.instr)
      src1 := io.in.instr(19, 15) // TODO: The pc needs src1 and imm to be updated
      src2 := 0.U //
      aluOp := ALUops.ALU_ADD // JALR uses addition to calculate target address
    }
    is(115.U) { // ECALL/EBREAK
      io.out.done := true.B
      // TODO: handle system instructions
    }
  }

  val isPC = Wire(Bool())
  isPC := opcode === 111.U || opcode === 23.U
  io.out.isPC := isPC

  io.out.src1 := src1 //Output register address, which gets translated in main file
  io.out.src2 := src2
  io.out.imm := imm
  io.out.aluOp := aluOp // Temporary, to be set based on instruction decoding
  io.out.dest := dest
  io.out.funct3 := funct3
  io.out.funct7 := funct7
}
/*
object DecodeStage extends App {
  emitVerilog(new DecodeStage(), Array("--target-dir", "generated"))
}*/
