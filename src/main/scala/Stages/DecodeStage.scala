import chisel3._
import chisel3.util._

class DecodeStage extends Module {

  // Helper function for sign-extending I-type immediates
  def signExtendIType(instr: UInt): UInt = {
    Cat(Fill(20, instr(31)), instr(31, 20))
  }
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
  val registerFile = Module(new RegisterFile())
  val src1 = RegInit(0.U(32.W))
  val src2 = RegInit(0.U(32.W))
  val dest = RegInit(0.U(32.W))
  val funct3 = RegInit(0.U(3.W))
  val opcode = Wire(UInt(7.W))
  opcode := io.instr(6, 0)
  val rd = Wire(UInt(5.W))
  rd := io.instr(11, 7)
  val funct7 = RegInit(0.U(1.W))

  switch(opcode) {
    is(19.U) { // I-Type
      val imm = signExtendIType(io.instr)
      src1 := (io.instr(19, 15))
      dest := rd
      funct3 := io.instr(14, 12)
      when(funct3 === 0x1.U || funct3 === 0x5.U) {
        src2 := imm(4,0)
        // TODO: handle funct7 for slli and srli
        funct7 := io.instr(30)
      }.otherwise {
        src2 := imm(11, 0)
      }
    } 
    is(51.U) { // R-type
      dest := rd
      src1 := io.instr(19, 15)
      src2 := (io.instr(31, 20)).asSInt().asUInt()
      funct3 := io.instr(14, 12)
      funct7 := io.instr(30)
    } 
    is(3.U) { // Load type
      val imm = signExtendIType(io.instr)
      src1 := (io.instr(19, 15))
      src2 := imm(11,0)
      funct3 := io.instr(14, 12)
      dest := rd
    } 
    is(35.U) { // Store type
      src1 := (io.instr(19, 15))
    } 
  }
  registerFile.io.readRegister1 := src1
  registerFile.io.readRegister2 := src2
  registerFile.io.writeRegister := 0.U
  registerFile.io.writeData := 0.U
  registerFile.io.regWrite := false.B
  io.aluOp := aluOp
  io.src1 := registerFile.io.readData1
  io.src2 := registerFile.io.readData2
  io.dest := dest
  io.funct3 := funct3
  io.funct7 := funct7

}

// You need this "Object" to run the generator!
object DecodeStage extends App {
  emitVerilog(new DecodeStage(), Array("--target-dir", "generated"))
}