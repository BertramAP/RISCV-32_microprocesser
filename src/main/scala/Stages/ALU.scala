package stages


import chisel3._
import chisel3.util._

class ALU extends Module {
  val io = IO(new Bundle {
    val in = Input(new DecodeExecuteIO)

    val aluOut = Output(UInt(32.W))
  })

  io.aluOut := 0.U

  // Extract the lower 5 bits for shift amount
  val shamt = io.in.src2(4, 0)

  switch (io.in.aluOp) {
    is(ALUops.ALU_ADD)  {
       io.aluOut := io.in.src1 + io.in.src2 
    }
    is(ALUops.ALU_SUB)  { 
      io.aluOut := io.in.src1 - io.in.src2 
    }
    is(ALUops.ALU_AND)  { 
      io.aluOut := io.in.src1 & io.in.src2 
    }
    is(ALUops.ALU_OR)   { 
      io.aluOut := io.in.src1 | io.in.src2 
    }
    is(ALUops.ALU_XOR)  { 
      io.aluOut := io.in.src1 ^ io.in.src2 
    }
    is(ALUops.ALU_SLT)  { 
      io.aluOut := (io.in.src1.asSInt < io.in.src2.asSInt).asUInt 
    }  
    is(ALUops.ALU_SLTU) { 
      io.aluOut := (io.in.src1 < io.in.src2).asUInt 
    }
    is(ALUops.ALU_SLL)  { 
      io.aluOut := (io.in.src1 << shamt)(31, 0) 
    }
    is(ALUops.ALU_SRL)  { 
      io.aluOut := io.in.src1 >> shamt 
    }  
    is(ALUops.ALU_SRA)  { 
      io.aluOut := (io.in.src1.asSInt >> shamt).asUInt 
    }
  }
}
