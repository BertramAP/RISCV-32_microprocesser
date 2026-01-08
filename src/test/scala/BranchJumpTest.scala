import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.AddiPipelineTop

class BranchingTest extends AnyFlatSpec with ChiselScalatestTester {
  private def countZeroPc(c: AddiPipelineTop, cycles: Int): Int = {
    var count = 0
    for (_ <- 0 until cycles) {
      c.clock.step()
      if (c.io.if_pc.peek().litValue == 0) {
        count += 1
      }
    }
    count
  }

  "AddiPipelineTop" should "loop back with BEQ" in {
    val program = Array(
      0x00000013, // nop
      0xfe000ee3, // beq x0, x0, -4
      0x00000013, // nop
      0x00000013  // nop
    )
    test(new AddiPipelineTop(program, 0)) { c =>
      val zeroCount = countZeroPc(c, 10)
      assert(zeroCount >= 2)
    }
  }

  it should "loop back with JAL" in {
    val program = Array(
      0x00000013, // nop
      0xffdff06f, // jal x0, -4
      0x00000013  // nop
    )
    test(new AddiPipelineTop(program, 0)) { c =>
      val zeroCount = countZeroPc(c, 10)
      assert(zeroCount >= 2)
    }
  }

  it should "loop back with JALR" in {
    val program = Array(
      0x00000013, // nop
      0x00000067, // jalr x0, x0, 0
      0x00000013  // nop
    )
    test(new AddiPipelineTop(program, 0)) { c =>
      val zeroCount = countZeroPc(c, 10)
      assert(zeroCount >= 2)
    }
  }
}
