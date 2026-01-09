package stages

import chisel3._
import chisel3.util._

class RegisterFile extends Module {
    val io = IO(new Bundle {
        val readRegister1 = Input( UInt(5.W) )
        val readRegister2 = Input( UInt(5.W) )
        val writeRegister = Input( UInt(5.W) )
        val regWrite = Input( Bool() )
        val writeData = Input( UInt(32.W) )
        val readData1 = Output( UInt(32.W) )
        val readData2 = Output( UInt(32.W) )
        val x1Full    = Output( Bool() ) // For debugging ADDI
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


    // For debugging only (Has data forwarding for read/write on same cycle)
    when ( writeThisCycle ) {
        when ( io.writeRegister === 1.U ) {
            io.x1Full := io.writeData =/= 0.U
        }
        .otherwise { io.x1Full := io.writeData }
    }
    .otherwise { io.x1Full := registers(1) =/= 0.U }
    // ^^^For debugging^^^
}