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
    

    val isBranch = io.in.PCSrc // forkert
    when (isBranch){
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

    // calculate branch target with a new adder
    val jaltarget := io.in.pc + io.in.imm
    val jalrtarget = (io.in.src1 + io.in.imm) & (~1.U(32.W))
    when (io.in.isJump) {
      io.BranchOut.branchTarget := Mux(io.in.isJump, jaltarget, jalrtarget)
    }

    val ALU = Module(new ALU())

    io.BranchOut.branchTarget := target
    io.BranchOut.branchTaken := false.B

    io.out.aluOut := ALU.io.aluOut
    io.out.addrWord := ALU.io.aluOut(4, 2)
    io.out.storeData := io.in.src2
    io.out.memRead := false.B
    io.out.memWrite := false.B
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
    io.out.regWrite := true.B // For simplicity, always write back
    io.out.memToReg := false.B
}