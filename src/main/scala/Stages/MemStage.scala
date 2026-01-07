package stages
import chisel3._
import chisel3.util._

class MemStage(depthWords: Int = 8) extends Module {
  require(depthWords == 8, s"depthWords must be 8 (got $depthWords)")

  val io = IO(new Bundle {
    // from EX/MEM
    val aluOutIn   = Input(UInt(32.W))
    val addrWord = Input(UInt(3.W))     // 0..7 (word index)
    val storeData = Input(UInt(32.W))
    val memRead = Input(Bool())
    val memWrite  = Input(Bool())

    val rd = Input(UInt(5.W))
    val regWrite  = Input(Bool())
    val memToReg   = Input(Bool())

    // to MEM/WB
    val memDataOut = Output(UInt(32.W))
    val aluOutOut  = Output(UInt(32.W)) 
    val wbRd       = Output(UInt(5.W))
    val wbRegWrite = Output(Bool())
    val wbMemToReg = Output(Bool())
  })

  val dmem = RegInit(VecInit(Seq.fill(8)(0.U(32.W))))

  val loadData = dmem(io.addrWord)

  when (io.memWrite) {
    dmem(io.addrWord) := io.storeData
  }

  io.memDataOut := loadData
  io.aluOutOut  := io.aluOutIn

  io.wbRd       := io.rd
  io.wbRegWrite := io.regWrite
  io.wbMemToReg := io.memToReg

  io.dbgMem := dmem
}
