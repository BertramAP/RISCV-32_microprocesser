import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.BenteTop

class BranchTest extends AnyFlatSpec with ChiselScalatestTester {
  "BranchTest" should "Correctly evaluate the equality of x1 and x0 and jump accordingly" in {
    val program = Array(
      0x00300093, // addi x1, x0, 3
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0xfff08093, // addi x1, x1, -1
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0xfe0098e3, // bne x1, x0, -16 (goto x1 instruction)
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
    )

    // Whether to print actual outputs for each stage
    val debug = true

    test( new BenteTop(program, 0) ) { dut =>
      for (i <- 0 until 26) { 
        if (debug) {
          //println()
          println("Instruction:   " + dut.io.if_instr.peek().litValue)
          //println("Branch taken:  " + dut.io.ex_branchTaken.peek().litValue)
          //println("Branch target: " + dut.io.ex_branchTarget.peek().litValue)
          //println("Register x1:   " + dut.io.debug_regFile(1).peek().litValue)
        }

        dut.clock.step(1) // Step clock
      }
    }
  }
}