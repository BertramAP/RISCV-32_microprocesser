import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.BenteTop

class ClockTest extends AnyFlatSpec with ChiselScalatestTester {
  "ADDI Test" should "Correctly calculate the result of ADDI and save result to x1" in {
  

    test( new Clock() ) { dut =>
      println( dut.io.state.peek().litValue )
      dut.clock.step(1)
      println( dut.io.state.peek().litValue )
      dut.clock.step(1)
      println( dut.io.state.peek().litValue )
    }
  }
}