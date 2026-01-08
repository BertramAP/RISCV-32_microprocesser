package stages

import chisel3._
import chisel3.util._

class Controller extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(7.W))
    val out = Output(new ControllerExecuteIO)
  })

  // Default values
  io.out.RegWrite := false.B
  io.out.ALUSrc := false.B
  io.out.PCSrc := false.B 
  io.out.MemRead := false.B
  io.out.MemWrite := false.B
  io.out.MemToReg := false.B
  io.out.isBranch := false.B
  io.out.isJump := false.B


  switch(io.opcode) {
    is("b0000011".U) { // Load
      io.out.RegWrite := true.B
      io.out.ALUSrc := true.B
      io.out.MemRead := true.B
      io.out.MemToReg := true.B
    }
    is("b0100011".U) { // Store
      io.out.ALUSrc := true.B
      io.out.MemWrite := true.B
    }
    is("b0010011".U) { // I-Type 
      io.out.RegWrite := true.B
      io.out.ALUSrc := true.B
    }
    is("b0110011".U) { // R-Type 
      io.out.RegWrite := true.B
    }
    is("b1100011".U) { // Branch
      io.out.isBranch := true.B
      io.out.PCSrc := true.B
    }
    is("b1101111".U) { // JAL
      io.out.RegWrite := true.B
      io.out.PCSrc := true.B
      io.out.isJump := true.B
      
  }
}
}

