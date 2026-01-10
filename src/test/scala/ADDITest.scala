import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.BenteTop

class ADDITest extends AnyFlatSpec with ChiselScalatestTester {
  "ADDI Test" should "Correctly calculate the result of ADDI and save result to x1" in {
    val program = Array(
      0x00300093, // addi x1, x0, 3
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0x00308093, // addi x1, x1, 3
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0xffe08093, // addi x1, x1, -2
    )

    // Whether to print actual outputs for each stage
    val debug = false

    // Expected values
    val expectedValues = Array( 0, 0, 0, 0, 0, 0, 3, 3, 3, 3, 6, 6, 6, 6, 4 )

    test( new BenteTop(program, 0) ) { dut =>
      for (i <- 0 until 15) {

        val x1 = dut.io.debug_regFile(1).peek().litValue
        if ( expectedValues(i) != x1 ) {
          fail("Failed at clock cycle: " + i + ". Expected x1 register value: " + x1 + " does not equal expected value: " + expectedValues(i))
        }

        if (debug) {
          println()
          println("Clock cycle: " + i)

          println("Fetch: ")
          println("Current instruction: " + dut.io.if_instr.peek().litValue )

          println("Writeback: ")
          println("Writeback signal:    " + dut.io.wb_wbEnable.peek().litValue )
          println("Writeback data:      " + dut.io.wb_wdata.peek().litValue )

          println("Decode: ")
          println("Decode immetidate:   " + dut.io.id_imm.peek().litValue )
          println("Decode read address: " + dut.io.id_readAddress1.peek().litValue )
          println("Decode read data 1:  " + dut.io.id_readData1.peek().litValue )

          println("ALU: ")
          println("ALU out:             " + dut.io.ex_aluOut.peek().litValue)

          println("Data: ")
          println("Data in x1:          " + dut.io.debug_regFile(1).peek().litValue)
          println("Data in x2:          " + dut.io.debug_regFile(2).peek().litValue)
        }

        dut.clock.step(1) // Step clock
      }
    }
  }
}