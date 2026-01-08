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
    

    io.BranchOut.branchTarget := 0.U
    io.BranchOut.branchTaken := false.B

    when (io.in.isBranch){
      switch(io.in.funct3){
        is("b000".U){ // BEQ
          when (io.in.src1 === io.in.src2){
            io.BranchOut.branchTaken := true.B
          } .otherwise {
            io.BranchOut.branchTaken := false.B
          }
        }
        is("b001".U){ // BNE
          when (io.in.src1 =/= io.in.src2){
            io.BranchOut.branchTaken := true.B
          } .otherwise {
            io.BranchOut.branchTaken := false.B
          }
        }
        is("b100".U){ // BLT
          when (io.in.src1.asSInt < io.in.src2.asSInt){
            io.BranchOut.branchTaken := true.B
          } .otherwise {
            io.BranchOut.branchTaken := false.B
          }
        }
        is("b101".U){ // BGE
          when (io.in.src1.asSInt >= io.in.src2.asSInt){
            io.BranchOut.branchTaken := true.B
          } .otherwise {
            io.BranchOut.branchTaken := false.B
          }
        }
        is("b110".U){ // BLTU
          when (io.in.src1 < io.in.src2){
            io.BranchOut.branchTaken := true.B
          } .otherwise {
            io.BranchOut.branchTaken := false.B
          }
        }
        is("b111".U){ // BGEU
          when (io.in.src1 >= io.in.src2){
            io.BranchOut.branchTaken := true.B
          } .otherwise {
            io.BranchOut.branchTaken := false.B
          }
        }
      }
    }

    val ALU = Module(new ALU())
    ALU.io.src1 := io.in.src1
    ALU.io.src2 := io.in.src2
    ALU.io.aluOp := io.in.aluOp

    when(io.BranchOut.branchTaken){
      io.BranchOut.branchTarget := ALU.io.aluOut
    }
    val jaltarget = io.in.pc + io.in.imm
    val jalrtarget = (io.in.src1 + io.in.imm) & (~1.U(32.W))
    when (io.in.isJump) {
      io.BranchOut.branchTarget := Mux(io.in.funct3 === 0.U, jaltarget, jalrtarget)
    }

    io.out.aluOut := ALU.io.aluOut
    io.out.addrWord := ALU.io.aluOut(4, 2)
    io.out.storeData := io.in.src2
    io.out.memRead := false.B
    io.out.memWrite := false.B
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
    io.out.regWrite := true.B // For simplicity, always write back
    io.out.memToReg := false.B
}
