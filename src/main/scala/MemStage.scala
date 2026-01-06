import chisel3._
import chisel3.util._

class MemStage(depthWords: Int = 8) extends Module {
  require(depthWords == 8, " words")

  val io = IO(new Bundle {
    // from EX/MEM
    val aluOut = Input(UInt(32.W))  
    val storeData = Input(UInt(32.W))
    val memRead = Input(Bool())
    val memWrite = Input(Bool())

    val rd = Input(UInt(5.W))
    val regWrite = Input(Bool())

    // to MEM/WB
    val wbData = Output(UInt(32.W))
    val wbRd = Output(UInt(5.W))
    val wbRegWrite = Output(Bool())

    // debugging output 
    val dbgMem = Output(Vec(8, UInt(32.W)))
  })

  // 8 x 32-bit words
  val dmem = RegInit(VecInit(Seq.fill(8)(0.U(32.W))))

  // byte offset 
  val wordAddr = io.aluOut(4, 2)

  // load data
  val loadData = dmem(wordAddr)

  // clocked memory write
  when(io.memWrite) {
    dmem(wordAddr) := io.storeData
  }

  // outputs
  io.wbData     := Mux(io.memRead, loadData, io.aluOut)
  io.wbRd       := io.rd
  io.wbRegWrite := io.regWrite

  // debugging output
  io.dbgMem := dmem
}
