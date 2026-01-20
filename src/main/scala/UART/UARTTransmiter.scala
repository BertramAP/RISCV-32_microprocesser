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
        val counter = RegInit(0.U(log2Ceil(countMAX + 1).W))
        
        val bitCounter = RegInit(0.U(4.W))
        val dataReg = RegInit(0.U(8.W))

        val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
        val state = RegInit(sIdle)
        //default values
        io.busy := true.B
        io.uartTx := true.B

        switch(state) {
            is(sIdle) {
                io.busy := false.B
                when(io.send) {
                    state := sStart
                    counter := 0.U
                    bitCounter := 0.U
                    dataReg := io.dataIn
                }
            }
            is(sStart) {
                io.uartTx := false.B // Start bit is low
                // Wait for one period
                when(counter === (countMAX-1).U) {
                    state := sData
                    counter := 0.U
                } .otherwise {
                    counter := counter + 1.U
                }
            }
            is(sData) {
                io.uartTx := dataReg(bitCounter) // Default to low, will be set to data bit
                when(counter === (countMAX-1).U) {
                    counter := 0.U
                    when(bitCounter === 7.U) {
                        state := sStop
                        bitCounter := 0.U
                    } .otherwise {
                        bitCounter := bitCounter + 1.U
                    }
                } .otherwise {
                    counter := counter + 1.U
                }
            }
            is(sStop) {
                io.uartTx := true.B // Stop bit is high
                when(counter === (countMAX-1).U) {
                    counter := 0.U
                    state := sIdle
                } .otherwise {
                    counter := counter + 1.U
                }
            }
        }
    }