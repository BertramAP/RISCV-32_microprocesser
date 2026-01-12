// For doing register dumps in the future
package UART

import chisel3._
import chisel3.util._

class UARTTransmiter() extends Module {
    val io = IO(new Bundle {
        val uartTx = Output(Bool()) // UART transmit line
        val dataIn = Input(UInt(8.W)) // Data to transmit
        val send = Input(Bool()) // Signal to start transmission
        val busy = Output(Bool()) // Indicates if transmitter is busy
    })
    val baudRate = 115200 // signal change per second
    val clockFreq = 100000000 // 100 MHz
    val countMAX = clockFreq / baudRate
    val countMID = countMAX / 2
    val counter = RegInit(0.U(10.W))

    val bitCounter = RegInit(0.U(4.W))

    val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
    val state = RegInit(sIdle)

    switch(state) {
        is(sIdle) {
            io.uartTx := true.B // Idle state is high
            io.busy := false.B
            when(io.send) {
                state := sStart
                counter := 0.U
                bitCounter := 0.U
            }
        }
        is(sStart) {
            io.uartTx := false.B // Start bit is low
            io.busy := true.B
            when(counter === (countMAX-1).U) {
                counter := 0.U
                state := sData
            } .otherwise {
                counter := counter + 1.U
            }
        }
        is(sData) {
            io.uartTx := io.dataIn

        }
    }
}