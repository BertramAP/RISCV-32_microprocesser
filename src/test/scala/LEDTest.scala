import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.BenteTop

class LEDTest extends AnyFlatSpec with ChiselScalatestTester {
  "LED Test" should "Should correctly toggle the LED once every 500ms" in {
    /*
    val program = Array(
      0x00000093, // addi x1 x0 0     | Init LED to off
      0x00000113, // addi x2 x0 0     | Set counter to 0
      // 0x007f31b7, // lui x3 2035      | Set counter target to 50M/6 = 8333333
      // 0x81518193, // addi x3 x3 -2027 | Set counter target to 50M/6 = 8333333
      0x01400193, // addi x3 x0 20
      0x00000013, // nop
      0x00000013, // nop

      // offloop:
      0x00110113, // addi x2 x2 1     | Increment counter
      0x00000013, // nop
      0x00000013, // nop
      0xfe311ae3, // bne x2 x3 -12    | Branch to offloop if we haven't reached target
      0x00000013, // nop
      0x00000013, // nop
      0x00100093, // addi x1 x0 1     | Turn LED ON

      // onloop:
      0xfff10113, // addi x2 x2 -1    | Decrement counter
      0x00000013, // nop
      0x00000013, // nop
      0xfe011ae3, // bne x2 x0 -12    | Branch to onloop if counter hasn't reached 0 yet
      0x00000013, // nop
      0x00000013, // nop
      0x00000093, // addi x1 x0 0     | Turn LED off
      0xfc0104e3, // beq x2 x0 -56    | Branch to offloop
      0x00000013, // nop
      0x00000013, // nop
    )

    // Whether to print actual outputs for each stage
    val debug = true


    test( new BenteTop(program, program, 0) ) { dut =>
      dut.clock.setTimeout(0) // Prevent test from failing after 1000 clock cycles

      var currentClock = 0

      for (i <- 0 until 260) {
        println("Current clock: " + currentClock + " current LED status: " + dut.io.led.peek().litValue)
        currentClock += 1;
        dut.clock.step(1)
      }
    }
    */
  }
}