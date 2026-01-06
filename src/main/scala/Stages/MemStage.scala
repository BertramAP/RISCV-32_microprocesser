import chisel3._
import chisel3.util._

class MemStage(depthWords: Int = 8) extends Module {
  require(depthWords == 8, s"depthWords must be 8 (got $depthWords)")

  val io = IO(new Bundle {
    // from EX/MEM
    val addrWord = Input(UInt(3.W))     // 0..7 (word index)
    val storeData = Input(UInt(32.W))
    val memRead = Input(Bool())
    val memWrite  = Input(Bool())

    val rd = Input(UInt(5.W))
    val regWrite  = Input(Bool())

    // to MEM/WB
    val wbData = Output(UInt(32.W))
    val wbRd = Output(UInt(5.W))
    val wbRegWrite = Output(Bool())

    // debug
    val dbgMem = Output(Vec(8, UInt(32.W)))
  })

  val dmem = RegInit(VecInit(Seq.fill(8)(0.U(32.W))))

  val loadData = dmem(io.addrWord)

  when (io.memWrite) {
    dmem(io.addrWord) := io.storeData
  }

  // If not memRead, you can output something else (often aluOut passthrough).
  // Since you removed aluOut, choose 0 or keep last value if you want.
  io.wbData := Mux(io.memRead, loadData, 0.U)
  io.wbRd := io.rd
  io.wbRegWrite := io.regWrite

  io.dbgMem := dmem
}
