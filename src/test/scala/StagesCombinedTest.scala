import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.AddiPipelineTop

class StagesCombinedTest extends AnyFlatSpec with ChiselScalatestTester {
  "AddiPipelineTop" should "execute a sequence of addi instructions" in {
    val program = Array(
      0x00100093,
      0x00200113,
      0x00300193,
      0x00400213,
      0x00000013,
      0x00000013
    )
    val expected = Seq(1, 2, 3, 4)
    val pcStart = 0
    test(new AddiPipelineTop(program, pcStart)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.clock.step(2)
      expected.foreach { value =>
        c.clock.step()
        c.io.ex_aluOut.expect(value.U)
      }
  }
}
}
