import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.BenteTop

class WriteBackTest extends AnyFlatSpec with ChiselScalatestTester {
  "WriteBackTest" should "Correctly propagate control signal" in {
    val program = Array(
      0x00100093, // addi x1, x0, 1
      0x00112023, // sw x1 0(x2)
      0x00100093, // addi x1, x0, 1
      0x00112023, // sw x1 0(x2)
      0x00112023, // sw x1 0(x2)
    )

    // The expected writeback output for each stage in each clock cycle
    val expectedValues = Array(
      Array(0, 0, 0, 0),
      Array(0, 0, 0, 0),
      Array(1, 0, 0, 0),
      Array(0, 1, 0, 0),
      Array(1, 0, 1, 0),
      Array(0, 1, 0, 1),
      Array(0, 0, 1, 0),
    )

    // Whether to print actual outputs for each stage
    val debug = false

    test( new BenteTop(program, 0) ) { dut =>
      for (i <- 0 until 7) {
        val currentValues = Array(
          dut.io.id_wbEnable.peek().litValue,
          dut.io.ex_wbEnable.peek().litValue,
          dut.io.mem_wbEnable.peek().litValue,
          dut.io.wb_wbEnable.peek().litValue
        )

        if (debug) {
          println()
          println("Clock cycle: " + i)
          println("Decode:      " + dut.io.id_wbEnable.peek().litValue)
          println("Execute:     " + dut.io.ex_wbEnable.peek().litValue)
          println("Memory:      " + dut.io.mem_wbEnable.peek().litValue)
          println("Writeback:   " + dut.io.wb_wbEnable.peek().litValue)
        }

        if ( !expectedValues(i).sameElements( currentValues ) ) {
          println("Expected values: " + expectedValues(i).mkString(" "))
          println("Current values: " + currentValues.mkString(" "))
          fail(s"Data mismatch at index " + i)
        }

        dut.clock.step(1)
      }
    }
  }
}