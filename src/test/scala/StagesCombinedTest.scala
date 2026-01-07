import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.AddiPipelineTop

class StagesCombinedTest extends AnyFlatSpec with ChiselScalatestTester {
// For now only Tests instruction -> alu out
  "AddiPipelineTop" should "execute a sequence of addi instructions" in {
    val program = Array(
      0x00100093, // addi x1, x0, 1
      0x00200113, // addi x2, x0, 2
      0x00300193, // addi x3, x0, 3
      0x00400213, // addi x4, x0, 4
      0x00000013, // nop (addi x0, x0, 0)
      0x00000013  // nop (addi x0, x0, 0)
    )
    val expected = Seq(1, 2, 3, 4)
    val pcStart = 0
    test(new AddiPipelineTop(program, pcStart)) { c =>
      c.clock.step(2)
      expected.foreach { value =>
        c.clock.step()
        c.io.ex_aluOut.expect(value.U)
      }
    }
  }
}
