package stages

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ImmediateAddressMemoryTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "store 3 words then load them back (VCD)" in {
    test(new ImmediateAddressMemoryTop).withAnnotations(Seq(
      WriteVcdAnnotation,
      TreadleBackendAnnotation
    )) { dut =>
      def stepInstr(instr: UInt, storeVal: UInt): Unit = {
        dut.io.instr.poke(instr)
        dut.io.storeValue.poke(storeVal)
        dut.clock.step(1)
      }

      val NOP = "h00000013".U

      // offsets by word (4 bytes)
      // sw x2, 4(x0)
      val SW_X2_4_X0  = "h00202223".U
      // sw x3, 8(x0)
      val SW_X3_8_X0  = "h00302423".U
      // sw x4, 12(x0)
      val SW_X4_12_X0 = "h00402623".U

      // lw x5, 4(x0)
      val LW_X5_4_X0  = "h00402283".U
      // lw x6, 8(x0)
      val LW_X6_8_X0  = "h00802303".U
      // lw x7, 12(x0)
      val LW_X7_12_X0 = "h00C02383".U

      val x2_val = "hDEADBEEF".U
      val x3_val = "hDEADDEAD".U  
      val x4_val = "hBEEFBEEF".U

      // reset
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)


      stepInstr(SW_X2_4_X0,  x2_val)  // mem[1] = DEADBEEF
      stepInstr(SW_X3_8_X0,  x3_val)  // mem[2] = 00DEADEA
      stepInstr(SW_X4_12_X0, x4_val)  // mem[3] = BEEFBEEF


      stepInstr(NOP, 0.U)

      dut.io.dbgMem(1).expect(x2_val)
      dut.io.dbgMem(2).expect(x3_val)
      dut.io.dbgMem(3).expect(x4_val)


      stepInstr(LW_X5_4_X0,  0.U)
      dut.io.memData.expect(x2_val)

      stepInstr(LW_X6_8_X0,  0.U)
      dut.io.memData.expect(x3_val)

      stepInstr(LW_X7_12_X0, 0.U)
      dut.io.memData.expect(x4_val)


      stepInstr(NOP, 0.U)
      dut.clock.step(2)
    }
  }
}
