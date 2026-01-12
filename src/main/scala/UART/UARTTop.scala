// For testing UART instruction loading
package UART

import chisel3._
import chisel3.util._

class UARTTop() extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool()) // UART receive line
    val led = Output(UInt(8.W)) // LED indicator
  })

  val instructionLoader = Module(new UARTInstructionLoader())
  instructionLoader.io.uartRx := io.rx

  // Simple LED indicator: turn on when loading is done
  val loadDoneReg = RegInit(0.U(8.W))
  when(instructionLoader.io.loadDone) {
    loadDoneReg := instructionLoader.io.transferData
  }
  io.led := loadDoneReg
}
object UARTTop extends App {
    emitVerilog(new UARTTop(), Array("--target-dir", "generated"))
}