import chisel3._
import chisel3.util._

class DecodeStage extends Module {
  val io = IO(new Bundle {
    // Inputs from the Fetch stage
    val instr = Input(0.U(32.W))
    val pc = Input(0.U(32.W))
    // Outputs to the Execute stage
    val aluOp = Output(0.U(4.W))
    val src1 = Output(0.U(32.W))
    val src2 = Output(0.U(32.W))
    val dest = Output(0.U(32.W))
    val funct3 = Output(0.U(3.W))
    val funct7 = Output(0.U(1.W))
  })
  val aluOp = RegInit(0.U(4.W))
  val src1 = RegInit(0.U(32.W))
  val src2 = RegInit(0.U(32.W))
  val dest = RegInit(0.U(32.W))
  val funct3 = RegInit(0.U(3.W))
  val opcode = RegInit(io.instr(6, 0))
  val rd = RegInit(io.instr(11, 7))
  val funct7 = RegInit(0.U(1.W))
  switch(opcode) {
    is(0x13.U) { // I-Type
      val imm = (io.instr(31, 20)).asSINT.pad(20).asUInt
      src1 := (io.instr(19, 15))
      dest := rd
      funct3 := io.instr(14, 12)
      when(funct3 === 0x1.U || funct3 === 0x5.U) {
        io.src2 := imm(4,0)
        // TODO: handle funct7 for slli and srli
        funct7 := io.instr(30)
      }.otherwise {
        src2 := imm
      }
    is(0x33.U) { // R-type
      dest := rd
      src1 := io.instr(19, 15)
      src2 := io.instr(24, 20)
      funct3 := io.instr(14, 12)
      funct7 := io.instr(30)
    } is(0x03.U) { // Load
      src1 := (io.instr(19, 15))
      src2 := (io.instr(31, 20)).asSINT.pad(20).asUInt
      funct3 := io.instr(14, 12)
      dest := rd
    } is(0x23.U) { // Store
      
    }

  // Implement the decode stage logic here
  }
  io.aluOp := aluOp
  io.src1 := src1
  io.src2 := src2
  io.dest := dest
  io.funct3 := funct3
  io.funct7 := funct7

}