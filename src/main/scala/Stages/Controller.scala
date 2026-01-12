package stages

import chisel3._
import chisel3.util._

class Controller extends Module {
  val io = IO(new Bundle {
    val opcode = Input(UInt(7.W))
    val out = Output(new ControllerExecuteIO)
  })

  // Default values
  io.out.regWrite := false.B
  io.out.aluSrc := false.B 
  io.out.memRead := false.B
  io.out.memWrite := false.B
  io.out.memToReg := false.B
  io.out.isBranch := false.B
  io.out.isJump := false.B
  io.out.isJumpr := false.B 
  io.out.done := false.B

  switch(io.opcode) {
    is("b0000011".U) { // Load
      io.out.regWrite := true.B
      io.out.aluSrc := true.B
      io.out.memRead := true.B
      io.out.memToReg := true.B
    }
    is("b0100011".U) { // Store
      io.out.aluSrc := true.B
      io.out.memWrite := true.B
    }
    is("b0010011".U) { // I-Type 
      io.out.regWrite := true.B
      io.out.aluSrc := true.B
    }
    is("b0110011".U) { // R-Type 
      io.out.regWrite := true.B
    }
    is("b1100011".U) { // Branch
      io.out.isBranch := true.B
      
    }
    is("b1101111".U) { // JAL
      io.out.regWrite := true.B
  
      io.out.isJump := true.B
    }
     is("b1100111".U) { // JALR
      io.out.regWrite := true.B
      io.out.isJumpr := true.B 
     }
     is("b0110111".U) { // LUI
       io.out.regWrite := true.B
       io.out.aluSrc := true.B
     }
     is("b0010111".U) { // AUIPC
       io.out.regWrite := true.B
       io.out.aluSrc := true.B
     }
     is("b1110011" .U) { // ECALL/EBREAK
      // No control signals asserted
      io.out.done := true.B
     }
  }
}


