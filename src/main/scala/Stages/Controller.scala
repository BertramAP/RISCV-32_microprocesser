// Part of the decode stage that interprets instructions
/*
import chisel3._
import chisel3.util._

class Controller extends Module {
  val io = IO(new Bundle {
    val opcode = Input(0.U(7.W))
    val aluOp = Output(0.U(.W))
  })

  switch(io.opcode) {
    is(0x13.U) { // I-Type
      val funct3 = 0.U(3.W)
      switch(funct3) {
        is(0x0.U) { // addi instruction
          io.aluOp := 2.U // ALU operation code for ADD
        }
        is(0x1.U) { // slli instruction
          io.aluOp := 1.U // ALU operation code for SLL
        }
        is(0x2.U) { // slti instruction
          io.aluOp := 2.U // ALU operation code for SLT
        }
      }
    }
    is(0x33) { // R-Type
      val funct3 = 0.U(3.W)
      val funct7 = 0.U(7.W)
      io.aluOp := 2.U // ALU operation code for R-Type
    

  }

  // Implement the control logic here
}
}

 */
