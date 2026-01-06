import chisel3.iotesters._
import org.scalatest._

class MemStageSpec extends FlatSpec with Matchers {
  "MemStage" should "store then load correctly" in {
    Driver.execute(Array("--generate-vcd-output", "on"), () => new MemStage()) { dut =>
      new PeekPokeTester(dut) {

        // STORE: mem[0] = 0xBEEFBEEF
        poke(dut.io.addrWord, 0)
        poke(dut.io.storeData, 0xBEEFBEEFL)
        poke(dut.io.memWrite, 1)
        poke(dut.io.memRead, 0)
        poke(dut.io.rd, 0)
        poke(dut.io.regWrite, 0)
        step(1)

        // STORE: mem[0] = 0xDEADEAD to overwrite
        poke(dut.io.addrWord, 0)
        poke(dut.io.storeData, 0xDEADEADL)
        poke(dut.io.memWrite, 1)
        poke(dut.io.memRead, 0)
        poke(dut.io.rd, 0)
        poke(dut.io.regWrite, 0)
        step(1)

        // LOAD: x5 = mem[0]
        poke(dut.io.addrWord, 0)
        poke(dut.io.memWrite, 0)
        poke(dut.io.memRead, 1)
        poke(dut.io.rd, 5)
        poke(dut.io.regWrite, 1)
        step(1)

        expect(dut.io.wbData, 0xDEADEADL)
        expect(dut.io.wbRd, 5)
        expect(dut.io.wbRegWrite, 1)
      }
    } should be(true)
  }
}
