import chisel3.iotesters._
import org.scalatest._

class MemStageSpec extends FlatSpec with Matchers {
  "MemStage" should "do store then load and also pass-through ALU results" in {
    Driver.execute(Array("--generate-vcd-output", "on"), () => new MemStage(256)) { dut =>
      new PeekPokeTester(dut) {

        // --------
        // STORE: mem[0] = 0xDEADBEEF
        // --------
        poke(dut.io.aluOut, 0)                 // address 0
        poke(dut.io.storeData, 0xDEADBEEFL)
        poke(dut.io.memWrite, 1)
        poke(dut.io.memRead, 0)
        poke(dut.io.rd, 0)
        poke(dut.io.regWrite, 0)
        step(1) // commit the write

        // --------
        // LOAD: x5 = mem[0]
        // --------
        poke(dut.io.aluOut, 0)     // address 0
        poke(dut.io.memWrite, 0)
        poke(dut.io.memRead, 1)
        poke(dut.io.rd, 5)
        poke(dut.io.regWrite, 1)
        step(1)

        expect(dut.io.wbData, 0xDEADBEEFL)
        expect(dut.io.wbRd, 5)
        expect(dut.io.wbRegWrite, 1)

        // --------
        // ALU pass-through (not a mem op)
        // --------
        poke(dut.io.memRead, 0)
        poke(dut.io.memWrite, 0)
        poke(dut.io.aluOut, 123)
        poke(dut.io.rd, 7)
        poke(dut.io.regWrite, 1)
        step(1)

        expect(dut.io.wbData, 123)
        expect(dut.io.wbRd, 7)
        expect(dut.io.wbRegWrite, 1)
      }
    } should be(true)
  }
}
