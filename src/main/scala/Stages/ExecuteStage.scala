package stages
import chisel3._
import chisel3.util._

class ExecuteStage(memSize: Int = 4096) extends Module {
    val io = IO(new Bundle {
      // Inputs from the Decode stage
      val in = Input(new DecodeExecuteIO)
      // Outputs to the Memory stage
      val out = Output(new ExecuteMemIO(memSize))
      // Outputs to the Fetch stage for branch handling
    val forwarding = Input(new ForwardingIO)
    val branchOut = Output(new FetchBranchIO)
    })

    // Forwarding Logic
    val src1_forwarded = Wire(UInt(32.W))
    val src2_forwarded = Wire(UInt(32.W))

    // Forwarding A (rs1)
    val forwardA_MEM = io.forwarding.mem_regWrite && io.forwarding.mem_rd =/= 0.U && io.forwarding.mem_rd === io.in.rs1_addr
    val forwardA_WB  = io.forwarding.wb_regWrite  && io.forwarding.wb_rd  =/= 0.U && io.forwarding.wb_rd  === io.in.rs1_addr

    when (forwardA_MEM) {
        src1_forwarded := io.forwarding.mem_aluOut
    } .elsewhen (forwardA_WB) {
        src1_forwarded := io.forwarding.wb_writeData
    } .otherwise {
        src1_forwarded := Mux(io.in.isPC, io.in.pc, io.in.src1) // Use values from src1 or PC
    }

    // Forwarding B (rs2)
    val forwardB_MEM = io.forwarding.mem_regWrite && io.forwarding.mem_rd =/= 0.U && io.forwarding.mem_rd === io.in.rs2_addr
    val forwardB_WB  = io.forwarding.wb_regWrite  && io.forwarding.wb_rd  =/= 0.U && io.forwarding.wb_rd  === io.in.rs2_addr

    when (forwardB_MEM) {
        src2_forwarded := io.forwarding.mem_aluOut
    } .elsewhen (forwardB_WB) {
        src2_forwarded := io.forwarding.wb_writeData
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

    io.branchOut.done := io.in.done
    io.branchOut.stall := false.B

    val branchCond = WireDefault(false.B)

    val src1_ext = Cat(0.U(1.W), src1_forwarded)
    val src2_ext = Cat(0.U(1.W), src2_forwarded)
    val diff = src1_ext - src2_ext
    val eq = diff === 0.U
    
    val sgn1 = src1_forwarded(31)
    val sgn2 = src2_forwarded(31)
    val slt = Mux(sgn1 === sgn2, diff(31), sgn1)
    val sltu = diff(32)

    when (io.in.isBranch) {
      switch(io.in.funct3) {
        is("b000".U) { branchCond := eq }      // BEQ
        is("b001".U) { branchCond := !eq }     // BNE
        is("b100".U) { branchCond := slt }     // BLT
        is("b101".U) { branchCond := !slt }    // BGE
        is("b110".U) { branchCond := sltu }    // BLTU
        is("b111".U) { branchCond := !sltu }   // BGEU
      }
    }
    val aluOut = WireDefault(0.U(32.W))
    val aluZero = WireDefault(false.B)

    // ALU Logic

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

    io.branchOut.branchTaken := (io.in.isBranch && branchCond) || (io.in.isJump || io.in.isJumpr)

    // Jump targets
    val jaltarget = io.in.pc + io.in.imm
    val jalrtarget = (src1_forwarded + io.in.imm) & (~1.U(32.W))
    io.branchOut.branchTarget := 0.U
    when(io.in.isJumpr) {
      io.branchOut.branchTarget := jalrtarget
    } .elsewhen(io.in.isJump) {
      io.branchOut.branchTarget := jaltarget
    } .elsewhen(io.in.isBranch && branchCond) {
      io.branchOut.stall := false.B // Stall logic implemented in BenteTop
      io.branchOut.branchTarget := io.in.pc + io.in.imm
    }
  
    when(io.in.isJump || io.in.isJumpr) {
      io.out.aluOut := io.in.pc + 4.U
    } .otherwise {
      io.out.aluOut := aluOut
    }
    val wMask = WireDefault(0.U(4.W))
    when(io.in.memWrite) {
      switch(io.in.funct3) {
        is(0.U) { wMask := 1.U }  // SB 
        is(1.U) { wMask := 3.U }  // SH 
        is(2.U) { wMask := 15.U } // SW 
        is(3.U) { wMask := 15.U }
      }
    }
    io.out.addrWord := aluOut(31, 2) // Word address for memory
    io.out.funct3 := io.in.funct3
    io.out.rd := io.in.dest(4, 0) // Truncate to 5 bits for register index
    val wBytes = VecInit(src2_forwarded(7,0), src2_forwarded(15,8), src2_forwarded(23,16), src2_forwarded(31,24))
    val bankData = Wire(Vec(4, UInt(8.W)))
    val bankMemWrite = Wire(Vec(4, Bool()))
    io.out.bankData := bankData
    val offset = aluOut(1, 0) // Byte offset within the 32-bit word address
    val aluOutWide = aluOut.asUInt.pad(log2Ceil(memSize * 4))
    val wordIdx = aluOutWide(log2Ceil(memSize * 4) - 1, 2) // Word index

    val bankAddr = Wire(Vec(4, UInt(log2Ceil(memSize).W)))
    for (i <- 0 until 4) {
      bankAddr(i) := Mux(i.U < offset, wordIdx + 1.U, wordIdx)
      val j = (i.U - offset)(1, 0)
      bankData(i) := wBytes(j)
      bankMemWrite(i) := io.in.memWrite && wMask(j)
    }
    io.out.bankData := bankData
    io.out.bankMemWrite := bankMemWrite
    io.out.bankAddr := bankAddr
}
