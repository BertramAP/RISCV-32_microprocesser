
import chisel3._
import chisel3.util._

class BranchPredictor extends Module {
  val io = IO(new Bundle {
    val isBranch = Input(Bool())
    val branchTaken = Input(Bool())

    val predictTaken = Output(Bool())
  })
  val counter = RegInit(0.U(2.W))

  switch(counter) {
    is(0.U) {
      when(io.branchTaken) {
        counter := 1.U
      }
      io.predictTaken := false.B
    }
    is(1.U) {
      when(io.branchTaken) {
        counter := 2.U
      } .otherwise {
        counter := 0.U
      }
    io.predictTaken := false.B
    }
    is(2.U) {
      when(io.branchTaken) {
        counter := 3.U
      } .otherwise {
        counter := 1.U
      }
      io.predictTaken := true.B
    }
    is(3.U) {
      when(!io.branchTaken) {
        counter := 2.U
      }
      io.predictTaken := true.B
    }
  }
}