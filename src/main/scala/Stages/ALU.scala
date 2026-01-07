package stages


import chisel3._
import chisel3.util._

class ALU extends Module {
  val io = IO(new Bundle {
    val src1 = Input(UInt(32.W))
    val src2 = Input(UInt(32.W))
    val aluOp = Input(UInt(4.W))
    
    val aluOut = Output(UInt(32.W))
    val zero = Output(Bool())
  })

  io.aluOut := 0.U

  // Extract the lower 5 bits for shift amount
  val shamt = io.src2(4, 0)

  switch (io.aluOp) {
    is(ALUops.ALU_ADD)  {
      io.aluOut := io.src1 + io.src2
    }
    is(ALUops.ALU_SUB)  {
      io.aluOut := io.src1 - io.src2
    }
    is(ALUops.ALU_AND)  {
      io.aluOut := io.src1 & io.src2
    }
    is(ALUops.ALU_OR)   {
      io.aluOut := io.src1 | io.src2
    }
    is(ALUops.ALU_XOR)  {
      io.aluOut := io.src1 ^ io.src2
    }
    is(ALUops.ALU_SLT)  {
      io.aluOut := (io.src1.asSInt < io.src2.asSInt).asUInt
    }
    is(ALUops.ALU_SLTU) {
      io.aluOut := (io.src1 < io.src2).asUInt
    }
    is(ALUops.ALU_SLL)  {
      io.aluOut := (io.src1 << shamt)(31, 0)
    }
    is(ALUops.ALU_SRL)  {
      io.aluOut := io.src1 >> shamt
    }
    is(ALUops.ALU_SRA)  {
      io.aluOut := (io.src1.asSInt >> shamt).asUInt
    }
  }

  io.zero := io.aluOut === 0.U
}
