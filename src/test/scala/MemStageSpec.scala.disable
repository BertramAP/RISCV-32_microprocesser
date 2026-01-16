import chisel3.iotesters._
import org.scalatest._
import stages.MemStage

class MemStageSpec extends FlatSpec with Matchers {
  "MemStage" should "store then load correctly" in {
    Driver.execute(Array("--generate-vcd-output", "on"), () => new MemStage(Array[Int]())) { dut =>
      new PeekPokeTester(dut) {

        // STORE: mem[0] = 0xBEEFBEEF
        poke(dut.io.in.done, 0)
        poke(dut.io.in.addrWord, 0)
        poke(dut.io.in.storeData, 0xBEEFBEEFL)
        poke(dut.io.in.memWrite, 1)
        poke(dut.io.in.memRead, 0)
        poke(dut.io.in.rd, 0)
        poke(dut.io.in.regWrite, 0)
        step(1)

        // STORE: mem[0] = 0xDEADEAD to overwrite
        poke(dut.io.in.addrWord, 0)
        poke(dut.io.in.storeData, 0xDEADEADL)
        poke(dut.io.in.memWrite, 1)
        poke(dut.io.in.memRead, 0)
        poke(dut.io.in.rd, 0)
        poke(dut.io.in.regWrite, 0)
        step(1)

        // LOAD: x5 = mem[0]
        poke(dut.io.in.addrWord, 0)
        poke(dut.io.in.memWrite, 0)
        poke(dut.io.in.memRead, 1)
        poke(dut.io.in.memToReg, 1)
        poke(dut.io.in.rd, 5)
        poke(dut.io.in.regWrite, 1)
        step(1)

        expect(dut.io.out.memData, 0xDEADEADL)
        expect(dut.io.out.wbRd, 5)
        expect(dut.io.out.wbRegWrite, 1)
      }
    } should be(true)
  }
}
