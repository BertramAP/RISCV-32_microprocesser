package stages
import chisel3._
import chisel3.util._

class ExecuteStage extends Module {
    val io = IO(new Bundle {
      // Inputs from the Decode stage
      val in = Input(new DecodeExecuteIO)
      // Outputs to the Memory stage
      val out = Output(new ExecuteMemIO)
    })
    
    val ALU = Module(new ALU())
    ALU.io.src1 := io.in.src1
    ALU.io.src2 := Mux(io.in.ALUSrc, io.in.src2, io.in.imm)

    io.out.aluOut := ALU.io.aluOut
    io.out.addrWord := ALU.io.aluOut(4, 2)
    io.out.storeData := io.in.src2
    io.out.memRead := io.in.MemRead
    io.out.memWrite := io.in.MemWrite
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
    io.out.regWrite := io.in.RegWrite
    io.out.memToReg := io.in.MemToReg
}