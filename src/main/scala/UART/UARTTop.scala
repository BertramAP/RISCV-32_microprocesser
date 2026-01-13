// For testing UART instruction loading
package UART

import chisel3._
import chisel3.util._

class UARTTop() extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool()) // UART receive line
    val led = Output(UInt(8.W)) // LED indicator
  })
  // memory
  val instructionMem = SyncReadMem(1024, UInt(8.W)) // Byte memory
  val dataMem = SyncReadMem(1024, UInt(8.W)) // Byte memory
  val memUsed = RegInit(0.U(1.W)) // 1 for data memory, 0 for instruction memory

  // boolean for loading header:
  val length = RegInit(0.U(24.W))
  val loadCounter = RegInit(0.U(2.W)) // length is 3 bytes, loaded in one by one
  val instructionLoader = Module(new UARTInstructionLoader())
  val pc = RegInit(0.U(32.W))
  instructionLoader.io.uartRx := io.rx


  
  val loadDoneReg = RegInit(0.U(8.W))
  /*
  // Simple LED indicator: turn on when loading is done
  io.led := loadDoneReg
  when(instructionLoader.io.loadDone) { // Write
    // TODO: concatenate bytes to form instructions, and reset instruction memory before loading new program
    instructionMem.write(pc, instructionLoader.io.transferData)
    loadDoneReg := instructionMem.read(pc)
    pc := pc + 1.U
    //loadDoneReg := instructionLoader.io.transferData
  } */ // From earlier tests

  val sIdle :: loadLength :: sLoadData :: Nil = Enum(3)
  val state = RegInit(sIdle)
  switch(state) {
    is(sIdle) { // We assume first loaded byte is an header
      when(instructionLoader.io.loadDone) {
        memUsed := instructionLoader.io.transferData(0) // LSB indicates memory type
        pc := 0.U // Reset pc for loading
        state := loadLength
      }
    }
    is(loadLength) {
      when(instructionLoader.io.loadDone) {
        length := Cat(instructionLoader.io.transferData(7,0), length(23, 8)) // Little endian
        loadCounter := loadCounter + 1.U
        when(loadCounter === 2.U) { // Should maybe be 3.U?
          state := sLoadData
          loadCounter := 0.U
        }
      }
    }
    is(sLoadData) {
      when(instructionLoader.io.loadDone) {
        // Ready to load data
        when(memUsed === 0.U) { // Instruction memory
          instructionMem.write(pc, instructionLoader.io.transferData)
          pc := pc + 1.U
          loadDoneReg := instructionMem.read(pc) // For LED indication
        }
        .otherwise { // Data memory
          when(instructionLoader.io.loadDone) {
            dataMem.write(pc, instructionLoader.io.transferData)
            loadDoneReg := dataMem.read(pc) // For LED indication
            pc := pc + 1.U
          }
        }
        when(pc === length - 1.U) {
          state := sIdle
        }
      }
    }
  }
  io.led := loadDoneReg

}
object UARTTop extends App {
    emitVerilog(new UARTTop(), Array("--target-dir", "generated"))
}