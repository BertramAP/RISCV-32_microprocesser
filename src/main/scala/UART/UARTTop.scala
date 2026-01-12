// For testing UART instruction loading
package UART

import chisel3._
import chisel3.util._

class UARTTop() extends Module {
  val io = IO(new Bundle {
    val uartRx = Input(Bool()) // UART receive line
    val io_led = Output(Bool())
  })

  val instructionLoader = Module(new UARTInstructionLoader())
  instructionLoader.io.uartRx := io.uartRx

  // Simple LED indicator: turn on when loading is done
  val loadDoneReg = RegInit(false.B)
  when(instructionLoader.io.loadDone) {
    loadDoneReg := true.B
  }
  io.io_led := loadDoneReg
}
object UARTTop extends App {
    emitVerilog(new UARTTop(), Array("--target-dir", "generated"))
}