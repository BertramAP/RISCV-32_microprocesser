import chisel3._

class Clock extends Module {
    val io = IO(new Bundle {
        val state = Output( Bool() )
    })

    val state = RegInit(false.B)
    state := !state
    io.state := state
}