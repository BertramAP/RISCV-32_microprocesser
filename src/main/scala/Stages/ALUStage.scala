import chisel3._
import chisel3.util._

class ALU extends Module {
    val io = IO(new Bundle {
        val readData1 = Input(UInt(32.W))
        val readData2 = Input(UInt(32.W))
        val result = Output(UInt(32.W))
        val writeData = Output(UInt(32.W))
    })

    val writeDataReg = RegInit(0.U(32.W))
    writeDataReg := io.readData2
    val resultReg = RegInit(0.U(32.W))
    resultReg := io.readData1 + io.readData2

    // Connecting outputs
    io.result := resultReg
    io.writeData := writeDataReg
}