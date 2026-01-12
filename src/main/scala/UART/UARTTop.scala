// For testing UART instruction loading
package UART

import chisel3._
import chisel3.util._

class UARTTop() extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool()) // UART receive line
    val led = Output(Bool())
  })

  val instructionLoader = Module(new UARTInstructionLoader())
  instructionLoader.io.uartRx := io.rx

  // Simple LED indicator: turn on when loading is done
  val loadDoneReg = RegInit(false.B)
  when(instructionLoader.io.loadDone) {
    loadDoneReg := true.B
  }
  io.led := loadDoneReg
}
object UARTTop extends App {
    emitVerilog(new UARTTop(), Array("--target-dir", "generated"))
}