import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.AddiPipelineTop

class ADDITest extends AnyFlatSpec with ChiselScalatestTester {
  "ADDI Test" should "Correctly calculate the result of ADDI and save result to x1" in {
    val program = Array(
      0x00100093, // addi x1, x0, 1
      0x00112023, // sw x1 0(x2)
      0x00112023, // sw x1 0(x2)
      0x00112023, // sw x1 0(x2)
      0x00112023, // sw x1 0(x2)
    )

    // Whether to print actual outputs for each stage
    val debug = true

    test( new AddiPipelineTop(program, 0) ) { dut =>
      for (i <- 0 until 10  ) {

        if (debug) {
          println()
          println("Clock cycle: " + i)
          println("Data in x1:       " + dut.io.x1Full.peek().litValue )
          println("Writeback signal: " + dut.io.wb_wbEnable.peek().litValue )
          println("Writeback data:   " + dut.io.wb_wdata.peek().litValue )
          println("ALU out:          " + dut.io.ex_aluOut.peek().litValue)
        }
        dut.clock.step(1)
      }
    }
  }
}