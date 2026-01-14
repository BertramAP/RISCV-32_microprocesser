package stages
import chisel3._
import chisel3.util._

class ExecuteStage extends Module {
    val io = IO(new Bundle {
      // Inputs from the Decode stage
      val in = Input(new DecodeExecuteIO)
      // Outputs to the Memory stage
      val out = Output(new ExecuteMemIO)
      // Outputs to the Fetch stage for branch handling
      val BranchOut = Output(new FetchBranchIO) 
    })

    // Forward control signals
    io.out.memRead := io.in.memRead
    io.out.memWrite := io.in.memWrite
    io.out.regWrite := io.in.regWrite
    io.out.memToReg := io.in.memToReg
    io.out.done := io.in.done

    io.BranchOut.done := io.in.done
    io.BranchOut.stall := false.B

    val branchCond = WireDefault(false.B)
  
    when (io.in.isBranch) {
      switch(io.in.funct3) {
        is("b000".U) { // BEQ
          branchCond := io.in.src1 === io.in.src2
        }
        is("b001".U) { // BNE
          branchCond := io.in.src1 =/= io.in.src2
        }
        is("b100".U) { // BLT
          branchCond := io.in.src1.asSInt < io.in.src2.asSInt
        }
        is("b101".U) { // BGE
          branchCond := io.in.src1.asSInt >= io.in.src2.asSInt
        }
        is("b110".U) { // BLTU
          branchCond := io.in.src1 < io.in.src2
        }
        is("b111".U) { // BGEU
          branchCond := io.in.src1 >= io.in.src2
        }
      }
    }

    val aluOut = WireDefault(0.U(32.W))
    val aluZero = WireDefault(false.B)

    // ALU Logic
    val src1 = io.in.src1
    val src2 = Mux(io.in.aluSrc, io.in.imm, io.in.src2)
    val aluOp = io.in.aluOp
    val shamt = src2(4, 0)
    
    switch (aluOp) {
      is(ALUops.ALU_ADD)  {
        aluOut := src1 + src2
      }
      is(ALUops.ALU_SUB)  {
        aluOut := src1 - src2
      }
      is(ALUops.ALU_AND)  {
        aluOut := src1 & src2
      }
      is(ALUops.ALU_OR)   {
        aluOut := src1 | src2
      }
      is(ALUops.ALU_XOR)  {
        aluOut := src1 ^ src2
      }
      is(ALUops.ALU_SLT)  {
        aluOut := (src1.asSInt < src2.asSInt).asUInt
      }
      is(ALUops.ALU_SLTU) {
        aluOut := (src1 < src2).asUInt
      }
      is(ALUops.ALU_SLL)  {
        aluOut := (src1 << shamt)(31, 0)
      }
      is(ALUops.ALU_SRL)  {
        aluOut := src1 >> shamt
      }
      is(ALUops.ALU_SRA)  {
        aluOut := (src1.asSInt >> shamt).asUInt
      }
    }
    aluZero := aluOut === 0.U

    io.BranchOut.branchTaken := (io.in.isBranch && branchCond) || (io.in.isJump || io.in.isJumpr)

    // Jump targets
    val jaltarget = io.in.pc + io.in.imm
    val jalrtarget = (io.in.src1 + io.in.imm) & (~1.U(32.W))
    io.BranchOut.branchTarget := 0.U
    when(io.in.isJumpr) {
      io.BranchOut.branchTarget := jalrtarget
    } .elsewhen(io.in.isJump) {
      io.BranchOut.branchTarget := jaltarget
    } .elsewhen(io.in.isBranch && branchCond) {
      io.BranchOut.stall := false.B // Stall logic implemented in BenteTop
      io.BranchOut.branchTarget := io.in.pc + io.in.imm
    }
  
    when(io.in.isJump || io.in.isJumpr) {
      io.out.aluOut := io.in.pc + 4.U
    } .otherwise {
      io.out.aluOut := aluOut
    }

    io.out.addrWord := aluOut(31, 2) // Word address for memory
    io.out.storeData := io.in.src2
    io.out.funct3 := io.in.funct3
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
}
