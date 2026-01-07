

import chisel3._
import chisel3.util._

class Controller extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(7.W))

    val Branch = Output(Bool())
    val RegWrite = Output(Bool())
    val ALUSrc = Output(Bool())
    val PCSrc = Output(Bool())
    val MemRead = Output(Bool())
    val MemWrite = Output(Bool())
    val MemToReg = Output(Bool())
  })

  // Default values
  io.RegWrite := false.B
  io.ALUSrc := false.B
  io.PCSrc := false.B
  io.MemRead := false.B
  io.MemWrite := false.B
  io.MemToReg := false.B

  switch(io.opcode) {
    is("b0000011".U) { // Load
      io.RegWrite := true.B
      io.ALUSrc := true.B
      io.MemRead := true.B
      io.MemToReg := true.B
    }
    is("b0100011".U) { // Store
      io.ALUSrc := true.B
      io.MemWrite := true.B
    }
    is("b0010011".U) { // I-Type 
      io.RegWrite := true.B
      io.ALUSrc := true.B
    }
    is("b0110011".U) { // R-Type 
      io.RegWrite := true.B
    }
    is("b1100011".U) { // Branch
      io.PCSrc := true.B
    }
  }
}

