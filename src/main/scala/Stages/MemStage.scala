package stages
import chisel3._
import chisel3.util._

class MemStage(depthWords: Int = 8) extends Module {
  require(depthWords == 8, s"depthWords must be 8 (got $depthWords)")

  val io = IO(new Bundle {
    // from EX/MEM
    val in = Input(new ExecuteMemIO)

    // to MEM/WB
    val out = Output(new MemWbIO)
    val dbgMem = Output(Vec(8, UInt(32.W)))
  })

  // Forward control signals to writeback stage
  io.out.wbRegWrite := io.in.regWrite
  io.out.wbMemToReg := io.in.memToReg

  val dmem = RegInit(VecInit(Seq.fill(8)(0.U(32.W))))

  val loadData = dmem(io.in.addrWord)

  when (io.in.memWrite) {
    dmem(io.in.addrWord) := io.in.storeData
  }

  io.out.memData := Mux(io.in.memRead, loadData, 0.U) // or just loadData
  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.wbRegWrite := io.in.regWrite
  io.out.wbMemToReg := io.in.memToReg
  io.dbgMem := dmem
}
