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

    // Forward control signals to memory stage
    io.out.memRead := io.in.MemRead
    io.out.memWrite := io.in.MemWrite
    io.BranchOut.done := io.in.done
    // Forward control signals to writeback stage
    io.out.regWrite := io.in.RegWrite
    io.out.memToReg := io.in.MemToReg

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

    val ALU = Module(new ALU())

    ALU.io.src1 := io.in.src1
    ALU.io.src2 := Mux(io.in.ALUSrc, io.in.imm, io.in.src2)
    ALU.io.aluOp := io.in.aluOp

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
      io.BranchOut.branchTarget := io.in.pc + io.in.imm
    }

    when (io.in.isJump || io.in.isJumpr) {
      io.out.aluOut := io.in.pc + 4.U
    } .otherwise {
      io.out.aluOut := ALU.io.aluOut
    }

    io.out.addrWord := ALU.io.aluOut(4, 2)
    io.out.storeData := io.in.src2
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
}
