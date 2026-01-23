package stages
import chisel3._
import chisel3.util._

class MemStage(data: Array[Int], memSize: Int = 4096) extends Module {
  val io = IO(new Bundle {
    val in = Input(new ExecuteMemIO(memSize))
    val out = Output(new MemWbIO)
    // inputs for InstructionTest   
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
  val mem1 = SyncReadMem(memSize, UInt(8.W))
  val mem2 = SyncReadMem(memSize, UInt(8.W))
  val mem3 = SyncReadMem(memSize, UInt(8.W))
  val banks = Seq(mem0, mem1, mem2, mem3)
  

  // Calculate address for each bank. If bank index < offset, it belongs to the next word.
  val rData = Wire(Vec(4, UInt(8.W)))
  val offset = io.in.aluOut(1, 0) // Byte offset within the 32-bit word address
  // Delay offset to match SyncReadMem latency (1 cycle) for data alignment
  val offsetReg = RegNext(offset)
  val wordIdx = io.in.aluOut(log2Ceil(memSize * 4) - 1, 2) // Word index

  val b0 = rData(offsetReg)
  val b1 = rData((offsetReg + 1.U)(1,0))
  val b2 = rData((offsetReg + 2.U)(1,0))
  val b3 = rData((offsetReg + 3.U)(1,0))
  
  io.out.memData := Cat(b3, b2, b1, b0)

  // Write Multiplexing

  // Test write data bytes
  val testWBytes = VecInit(io.dmemWdata(7,0), io.dmemWdata(15,8), io.dmemWdata(23,16), io.dmemWdata(31,24))


  for (i <- 0 until 4) {
    // Write mask for each byte
    val writeEnable = io.dmemWe || io.in.bankMemWrite(i)
    // Map global byte index to data index: j = (BankI - offset) % 4
    
    // Multiplexing for testing
    val writeAddr = Mux(io.dmemWe, io.dmemWaddr, io.in.bankAddr(i))
    val writeData = Mux(io.dmemWe, testWBytes(i), io.in.bankData(i))

    rData(i) := banks(i).read(io.in.bankAddr(i))

    when(writeEnable) {
      banks(i).write(writeAddr, writeData)
    }
  }

  io.out.aluOut     := io.in.aluOut
  io.out.wbRd       := io.in.rd
  io.out.done       := io.in.done 
}