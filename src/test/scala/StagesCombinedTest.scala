import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.BenteTop

class StagesCombinedTest extends AnyFlatSpec with ChiselScalatestTester {
  "BenteTop" should "execute a sequence of addi instructions" in {
    val program = Array(
      0x00100093, // addi x1, x0, 1
      0x00200113, // addi x2, x0, 2
      0x00300193, // addi x3, x0, 3
      0x00400213, // addi x4, x0, 4
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0x00000013, // nop
      0x00000073, // ebreak
      0x00400213 // addi x4, x0, 4 test if ebreak works
    )
    val pcStart = 0
    test(new BenteTop(program, pcStart)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // An instruction's result is written back to the register file in the WB stage.
      // For a 5-stage pipeline, the first instruction writes back at the end of cycle 4. We have a extra register somewhere But it works with 5 for now.
      c.clock.step(5) // Run until the end of cycle 3

      // At end of cycle 4, x1 should be 1
      c.clock.step()
      c.io.debug_regFile(1).expect(1.U)

      // At end of cycle 5, x2 should be 2
      c.clock.step()
      c.io.debug_regFile(2).expect(2.U)

      // At end of cycle 6, x3 should be 3
      c.clock.step()
      c.io.debug_regFile(3).expect(3.U)

      // At end of cycle 7, x4 should be 4
      c.clock.step()
      c.io.debug_regFile(4).expect(4.U)
    }
  }
}
