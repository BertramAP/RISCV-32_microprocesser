package Stages

import chisel3._
import chisel3.util._

class RegisterFile extends Module {
    val io = IO(new Bundle {
        val readRegister1 = Input( UInt(5.W) )
        val readRegister2 = Input( UInt(5.W) )
        val writeRegister = Input( UInt(5.W) )
        val writeData = Input( UInt(32.W) )
        val regWrite = Input( Bool() )
        val readData1 = Output( UInt(32.W) )
        val readData2 = Output( UInt(32.W) )
    })

    val registers = RegInit(VecInit( Seq.fill(32)(0.U(32.W)) ))
    val writeThisCycle = io.regWrite && io.writeRegister =/= 0.U

    // Reading data
    io.readData1 := registers( io.readRegister1 )
    io.readData2 := registers( io.readRegister2 )

    // Writing data
    when ( writeThisCycle ) {
        registers(io.writeRegister) := io.writeData

        // Forward new register value if we read from the same cycle as we write
        when ( io.readRegister1 === io.writeRegister ) {
            io.readData1 := io.writeData
        }
        when ( io.readRegister2 === io.writeRegister ) {
            io.readData2 := io.writeData
        }
    }
}