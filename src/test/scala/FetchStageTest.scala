import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FetchStageTest extends AnyFlatSpec with ChiselScalatestTester {

  "FetchStage" should "fetch a program correctly" in {
    val program = Array(
      0x00100093, // addi x1, x0, 1
      0x00200113, // addi x2, x0, 2
      0x00300193, // addi x3, x0, 3
      0x00400213  // addi x4, x0, 4
    )
    val pcStart = 0

    test(new FetchStage(program, pcStart)) { c =>
      for (i <- program.indices) {
        // PC should be at the correct address for the current instruction
        c.io.pc.expect((pcStart + i * 4).U)
        // The instruction should match what's in our program memory
        c.io.instr.expect(program(i).U)

        // Step the clock to fetch the next instruction
        c.clock.step(1)
      }

      // After fetching all instructions, PC should be at the address of the next instruction
      c.io.pc.expect((pcStart + program.length * 4).U)
    }
  }
}
