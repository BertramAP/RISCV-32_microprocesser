package stages
import chisel3._
import chisel3.util._
import chisel3.experimental.{annotate, ChiselAnnotation}
import firrtl.AttributeAnnotation

class MemStage(data: Array[Int], memSize: Int = 4096) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExecuteMemIO)
    val out = Output(new MemWbIO)
    val dmemWe    = Input(Bool())
    val dmemWaddr = Input(UInt(log2Ceil(memSize).W))
    val dmemWdata = Input(UInt(32.W))

  })
  
  io.out.pc := io.in.pc
  io.out.wbRegWrite := io.in.regWrite
  io.out.wbMemToReg := io.in.memToReg
  io.out.done       := io.in.done
  io.out.funct3     := io.in.funct3

  // Split memory into 4 banks each bank is 1 byte wide
  val mem0 = SyncReadMem(memSize, UInt(8.W))
  annotate(new ChiselAnnotation {
    def toFirrtl = AttributeAnnotation(mem0.toTarget, "ram_style = \"block\"")
  })
  val mem1 = SyncReadMem(memSize, UInt(8.W))
  annotate(new ChiselAnnotation {
    def toFirrtl = AttributeAnnotation(mem1.toTarget, "ram_style = \"block\"")
  })
  val mem2 = SyncReadMem(memSize, UInt(8.W))
  annotate(new ChiselAnnotation {
    def toFirrtl = AttributeAnnotation(mem2.toTarget, "ram_style = \"block\"")
  })
  val mem3 = SyncReadMem(memSize, UInt(8.W))
  annotate(new ChiselAnnotation {
    def toFirrtl = AttributeAnnotation(mem3.toTarget, "ram_style = \"block\"")
  })
  val banks = Seq(mem0, mem1, mem2, mem3)
  
  // Address Decoding
  val addr = io.in.aluOut
  val offset = addr(1, 0)
  val wordIdx = addr(log2Ceil(memSize * 4) - 1, 2) // Word index

  // Calculate address for each bank. If bank index < offset, it belongs to the next word.
  val rData = Wire(Vec(4, UInt(8.W)))
  for (i <- 0 until 4) {
    val bankAddr = Mux(i.U < offset, wordIdx + 1.U, wordIdx)
    rData(i) := banks(i).read(bankAddr)
  }

  // Delay offset to match SyncReadMem latency (1 cycle) for data alignment
  val offsetReg = RegNext(offset)

  val b0 = rData(offsetReg)
  val b1 = rData((offsetReg + 1.U)(1,0))
  val b2 = rData((offsetReg + 2.U)(1,0))
  val b3 = rData((offsetReg + 3.U)(1,0))
  
  io.out.memData := Cat(b3, b2, b1, b0)

  // Write Multiplexing
  val wMask = WireDefault(0.U(4.W))
  when(io.in.memWrite) {
    switch(io.in.funct3) {
      is(0.U) { wMask := 1.U }  // SB (Byte 0)
      is(1.U) { wMask := 3.U }  // SH (Bytes 0, 1)
      is(2.U) { wMask := 15.U } // SW (Bytes 0, 1, 2, 3)
      is(3.U) { wMask := 15.U }
    }
  }

  val wBytes = VecInit(io.in.storeData(7,0), io.in.storeData(15,8), io.in.storeData(23,16), io.in.storeData(31,24))

  // Debug write data bytes
  val debugWBytes = VecInit(io.dmemWdata(7,0), io.dmemWdata(15,8), io.dmemWdata(23,16), io.dmemWdata(31,24))


  for (i <- 0 until 4) {
    // --- Processor Logic ---
    // Map global byte index to data index: j = (BankI - offset) % 4
    val j = (i.U - offset)(1, 0)
    // Processor address for this bank
    val processorBankAddr = Mux(i.U < offset, wordIdx + 1.U, wordIdx)
    // Processor write enable for this bank
    val processorWe = io.in.memWrite && wMask(j)
    val processorData = wBytes(j)

    // --- Multiplexing (Debug priority) ---
    val writeEnable = io.dmemWe || processorWe
    val writeAddr = Mux(io.dmemWe, io.dmemWaddr, processorBankAddr)
    val writeData = Mux(io.dmemWe, debugWBytes(i), processorData)

    when(writeEnable) {
      banks(i).write(writeAddr, writeData)
    }
  }

  io.out.aluOut  := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done 
}