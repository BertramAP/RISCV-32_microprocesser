import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.AddiPipelineTop

class StagesCombinedTest extends AnyFlatSpec with ChiselScalatestTester {

  "AddiPipelineTop" should "execute a single addi instruction" in {
    test(new AddiPipelineTop) { c =>
      // Step through the pipeline (reset -> IF -> ID -> EX/MEM)
      c.clock.step(4)

      c.io.if_instr.expect("h00100093".U) // addi x1, x0, 1
      c.io.ex_rd.expect(1.U)
      c.io.ex_aluOut.expect(1.U)
    }
  }
}
