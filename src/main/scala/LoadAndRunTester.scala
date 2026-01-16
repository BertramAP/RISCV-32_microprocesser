package stages

import chisel3._
import chisel3.util._
import UART.UARTInstructionLoader
import spire.std.byte

class LoadAndRunTester(memSizeWords: Int = 128, PcStart: Int = 0) extends Module {
  val io = IO(new Bundle {
    val rx  = Input(Bool()) // -> io_rx in XDC      
    val tx  = Output(Bool()) // -> io_tx in XDC
    val buy = Input(Bool()) // Start button input
    val led = Output(UInt(8.W)) // LED indicators
  })


  val core = Module(new BenteTop(
    Array.fill(memSizeWords)(0),
    Array.fill(memSizeWords)(0),
    PcStart,
    memSizeWords
  ))


  // UART byte receiver
  val uart = Module(new UARTInstructionLoader())
  uart.io.uartRx := io.rx
  // Loader FSM: header (memUsed) + 3-byte length + payload bytes
  // Loader FSM: header (memUsed) + 3-byte length + payload bytes
  val sIdle :: sLen :: sData :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val memUsed     = RegInit(0.U(1.W))     // 0=IMEM, 1=DMEM
  val lengthBytes = RegInit(0.U(24.W))
  val lenCount    = RegInit(0.U(2.W))     // 0..2

  val byteIndex   = RegInit(0.U(24.W))
  val byteCounter = RegInit(0.U(2.W))     // 0..3
  val wordBuffer  = RegInit(0.U(32.W))

  val imemLoaded = RegInit(false.B)
  val dmemLoaded = RegInit(false.B)

  // default: no writes
  core.io.imemWe := false.B
  core.io.imemWaddr := 0.U
  core.io.imemWdata := 0.U

  core.io.dmemWe := false.B
  core.io.dmemWaddr := 0.U
  core.io.dmemWdata := 0.U

  // any UART activity => core is not running
  val loadingActive = state =/= sIdle

  // button rising edge
  val buySync0 = RegNext(io.buy)
  val buySync1 = RegNext(buySync0)
  val buyPrev  = RegNext(buySync1)
  val buyRise  = buySync1 && !buyPrev

  val runReg = RegInit(false.B)
  val loadedAll = imemLoaded && dmemLoaded

  when(loadingActive) {
    runReg := false.B // must press button after loading
  }.elsewhen(buyRise && loadedAll) {
    runReg := true.B
  }
  // Registers for FSM
  val coreImemWeReg   = RegInit(false.B)
  val coreImemAddrReg = RegInit(0.U(32.W))
  val coreImemDataReg = RegInit(0.U(32.W))

  val coreDmemWeReg   = RegInit(false.B)
  val coreDmemAddrReg = RegInit(0.U(32.W))
  val coreDmemDataReg = RegInit(0.U(32.W))
  // Default: no writes
  coreImemWeReg := false.B
  coreDmemWeReg := false.B

  switch(state) {
    is(sIdle) {
      byteIndex := 0.U
      lenCount := 0.U
      byteCounter := 0.U

      when(uart.io.loadDone) {
        memUsed := uart.io.transferData(0)
        state := sLen
      }
    }
    
    is(sLen) {
      when(uart.io.loadDone) {
        lengthBytes := Cat(uart.io.transferData, lengthBytes(23, 8)) 
        lenCount := lenCount + 1.U
        when(lenCount === 2.U) {
          lenCount := 0.U
          state := sData
        }
      }
    }

    is(sData) {
      when(uart.io.loadDone) {
        val shifted = (uart.io.transferData.asUInt << (byteCounter << 3)).asUInt
        val newWord = wordBuffer | shifted
        wordBuffer := newWord

        val atWordEnd = (byteCounter === 3.U)
        val wordAddr = (byteIndex >> 2).asUInt

        when(atWordEnd) {
          when(memUsed === 0.U) {
            coreImemWeReg := true.B
            coreImemAddrReg := wordAddr(log2Ceil(memSizeWords)-1, 0)
            coreImemDataReg := newWord
          }.otherwise {
            coreDmemWeReg := true.B
            coreDmemAddrReg := wordAddr(log2Ceil(memSizeWords)-1, 0)
            coreDmemDataReg := newWord
          }
          wordBuffer := 0.U
          byteCounter := 0.U
        }.otherwise {
          byteCounter := byteCounter + 1.U
        }

        val lastByte = (byteIndex === (lengthBytes - 1.U))
        byteIndex := byteIndex + 1.U

        when(lastByte) {
          when(memUsed === 0.U) { imemLoaded := true.B } .otherwise { dmemLoaded := true.B }
          state := sIdle
        }
      }
    }
  }

  val doneLatched = RegInit(false.B)
  when(loadingActive || buyRise) {
    doneLatched := false.B
  }.elsewhen(core.io.done) {
    doneLatched := true.B
  }
  val transmiter = Module(new UART.UARTTransmiter())
  io.tx := transmiter.io.uartTx
  when(doneLatched) {
    transmiter.io.dataIn := core.io.debug_regFile(10)(7,0) // example: send reg x10 (a0) LSB
    transmiter.io.send := true.B
  }.otherwise {
    transmiter.io.dataIn := 0.U
    transmiter.io.send := false.B
  }
  core.io.run := runReg && loadedAll && !loadingActive && !doneLatched
  
  core.io.imemWe    := coreImemWeReg
  core.io.imemWaddr := coreImemAddrReg
  core.io.imemWdata := coreImemDataReg
  
  core.io.dmemWe    := coreDmemWeReg
  core.io.dmemWaddr := coreDmemAddrReg
  core.io.dmemWdata := coreDmemDataReg
  /* LED map:
  [0] loadingActive
  [1] loadedAll
  [2] running (core.io.run)
  [3] doneLatched
  [4] core LED output (x1===1)
  [7:5] unused */
  io.led := Cat(0.U(2.W), core.io.led, doneLatched, core.io.done, core.io.run, loadedAll, loadingActive)
}

object LoadAndRunTester extends App {
  emitVerilog(new LoadAndRunTester(), Array("--target-dir", "generated"))
}
