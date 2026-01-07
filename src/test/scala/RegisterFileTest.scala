import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.RegisterFile

class RegisterFileTest extends AnyFlatSpec with ChiselScalatestTester {
  "RegisterFile" should "Correctly read/write data" in {
    test( new RegisterFile() ) { dut =>
      dut.io.readRegister1.poke(1.U) // Keep outputting x1
      dut.io.readRegister2.poke(2.U) // Keep outputting x2
      
      // Don't write to a register
      dut.io.writeRegister.poke(1.U)
      dut.io.regWrite.poke(false.B)
      dut.io.writeData.poke(1.U)
      dut.clock.step(1)
      
      // Expected both x1 and x2 to equal 0
      dut.io.readData1.expect(0)
      dut.io.readData2.expect(0)

      // Now do x1 = 1. We're now reading and writing
      // on same clock cycle so value should just be
      // forwarded
      dut.io.regWrite.poke(true.B)
      dut.clock.step(1)
      dut.io.readData1.expect(1)

      // Now read x1 again while writing x2 = 2. We should get
      // the saved value x1 = 1 and the forwarded value x2 = 2
      dut.io.writeRegister.poke(2.U)
      dut.io.writeData.poke(2.U)
      dut.clock.step(1)
      dut.io.readData1.expect(1)
      dut.io.readData2.expect(2)
    }
  }
}