// For testing UART instruction loading
package UART

import chisel3._
import chisel3.util._

class UARTTop() extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool()) // UART receive line
    val led = Output(Bool())
  })
  val instructionMem = SyncReadMem(1024, UInt(32.W)) // 256 bytes instruction memory
  val instructionLoader = Module(new UARTInstructionLoader())
  val pc = RegInit(0.U(32.W))
  instructionLoader.io.uartRx := io.rx

  // Simple LED indicator: turn on when loading is done
  io.led := instructionLoader.io.loadDone
  when(instructionLoader.io.loadDone) { // Write
    instructionMem.write(pc, instructionLoader.io.transferData)
    pc := pc + 1.U
  }
}
object UARTTop extends App {
    emitVerilog(new UARTTop(), Array("--target-dir", "generated"))
}