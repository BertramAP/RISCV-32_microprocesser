
import chisel3._
import chisel3.util._

class UARTInstructionLoader() extends Module {
    val io = IO(new Bundle {
    val uartRx = Input(Bool())
    val loadDone = Output(Bool())

  })
  val baudRate = 115200
  val clockFreq = 100000000 // 100 MHz
  val divisor = clockFreq / baudRate

  
}