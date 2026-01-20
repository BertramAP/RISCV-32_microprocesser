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
    val IO_forwarding = Input(new Bundle {
        val mem_rd = UInt(5.W)
        val mem_regWrite = Bool()
        val mem_aluOut = UInt(32.W)
        
        val wb_rd = UInt(5.W)
        val wb_regWrite = Bool()
        val wb_writeData = UInt(32.W)
    })
    val BranchOut = Output(new FetchBranchIO)
    })

    // Forwarding Logic
    val src1_forwarded = Wire(UInt(32.W))
    val src2_forwarded = Wire(UInt(32.W))

    // Forwarding A (rs1)
    val forwardA_MEM = io.IO_forwarding.mem_regWrite && io.IO_forwarding.mem_rd =/= 0.U && io.IO_forwarding.mem_rd === io.in.rs1_addr
    val forwardA_WB  = io.IO_forwarding.wb_regWrite  && io.IO_forwarding.wb_rd  =/= 0.U && io.IO_forwarding.wb_rd  === io.in.rs1_addr

    when (forwardA_MEM) {
        src1_forwarded := io.IO_forwarding.mem_aluOut
    } .elsewhen (forwardA_WB) {
        src1_forwarded := io.IO_forwarding.wb_writeData
    } .otherwise {
        src1_forwarded := Mux(io.in.isPC, io.in.pc, io.in.src1) // Use values from buffer (or PC)
    }

    // Forwarding B (rs2)
    val forwardB_MEM = io.IO_forwarding.mem_regWrite && io.IO_forwarding.mem_rd =/= 0.U && io.IO_forwarding.mem_rd === io.in.rs2_addr
    val forwardB_WB  = io.IO_forwarding.wb_regWrite  && io.IO_forwarding.wb_rd  =/= 0.U && io.IO_forwarding.wb_rd  === io.in.rs2_addr

    when (forwardB_MEM) {
        src2_forwarded := io.IO_forwarding.mem_aluOut
    } .elsewhen (forwardB_WB) {
        src2_forwarded := io.IO_forwarding.wb_writeData
    } .otherwise {
        src2_forwarded := io.in.src2
    }
    
    // ALU Inputs
    val src1 = src1_forwarded
    val src2 = Mux(io.in.aluSrc, io.in.imm, src2_forwarded) // Imm overrides src2 (even if forwarded)



    // Forward control signals
    io.out.pc := io.in.pc
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
          branchCond := src1_forwarded === src2_forwarded
        }
        is("b001".U) { // BNE
          branchCond := src1_forwarded =/= src2_forwarded
        }
        is("b100".U) { // BLT
          branchCond := src1_forwarded.asSInt < src2_forwarded.asSInt
        }
        is("b101".U) { // BGE
          branchCond := src1_forwarded.asSInt >= src2_forwarded.asSInt
        }
        is("b110".U) { // BLTU
          branchCond := src1_forwarded < src2_forwarded
        }
        is("b111".U) { // BGEU
          branchCond := src1_forwarded >= src2_forwarded
        }
      }
    }

    val aluOut = WireDefault(0.U(32.W))
    val aluZero = WireDefault(false.B)

    // ALU Logic
    // src1 and src2 are already defined above with forwarding logic

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
    val jalrtarget = (src1_forwarded + io.in.imm) & (~1.U(32.W))
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
    io.out.storeData := src2_forwarded
    io.out.funct3 := io.in.funct3
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
}
