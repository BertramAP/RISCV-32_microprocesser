
import chisel3._
import chisel3.util._

class UARTInstructionLoader() extends Module {
    val io = IO(new Bundle {
    val uartRx = Input(Bool()) // UART receive line
    val loadDone = Output(Bool())
    val transferData = Output(UInt(8.W))
  });
  val baudRate = 115200 // signal change per second
  val clockFreq = 100000000 // 100 MHz
  val countMAX = clockFreq / baudRate 
  val countMID = countMAX / 2
  val counter = RegInit(0.U(10.W))
  
  val byteCounter = RegInit(0.U(3.W))
  val dataReg = RegInit(0.U(8.W))

  val sIdle :: sStart :: sData :: sStop :: sDone :: Nil = Enum(5)
  val state = RegInit(sIdle)
  io.loadDone := false.B
  io.transferData := 0.U

  switch(state) {
    is(sIdle) {
      when(!io.uartRx) {
        state := sStart
      }
    }
   is(sStart) {
    when(counter === countMID) {
      when(!io.uartRx) {
        state := sData
      } .otherwise { // False alarm
        state := sIdle
      }
    } .otherwise {
      counter := counter + 1.U
    }
   }
   is(sData) {
    when(counter === countMAX) {
      counter := 0.U // Reset counter
      dataReg := Cat(io.uartRx, dataReg(7,1))
      when(byteCounter === 7.U) {
        state := sStop
        byteCounter := 0.U
        } .otherwise {
        byteCounter := byteCounter + 1.U
        }
    } .otherwise {
      counter := counter + 1.U
    }
    }
    is(sStop) {
    when(counter === countMAX) {
      when(io.uartRx) {
        state := sDone
      } .otherwise { // Framing error, go back to idle
        state := sIdle
      }
      counter := 0.U
    } .otherwise {
      counter := counter + 1.U
    }
   }
   is(sDone) {
    io.loadDone := true.B
    io.transferData := dataReg
    state := sIdle
   }
}
}