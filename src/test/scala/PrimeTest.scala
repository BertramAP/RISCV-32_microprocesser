
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.ElfLoader
import stages.BenteTop
import java.nio.file.Paths

class PrimeTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Prime Benchmark"

  it should "run prime.bin until completion" in {
    val binFile = "tests/Prime_benchmark/prime.bin"
    val (imem, dmem, start) = ElfLoader.load(binFile)

    // Using 16KB memory size as used in InstructionTest (4096 words)
    val dmemSize = 4096 
    val newDmem = new Array[Int](dmemSize)
    val copyLen = math.min(dmem.length, dmemSize)
    Array.copy(dmem, 0, newDmem, 0, copyLen)

    test(new BenteTop(imem, newDmem, start, dmemSize)) { c =>
      // Explicitly initialize inputs
      c.io.run.poke(false.B)
      c.io.imemWe.poke(false.B)
      c.io.dmemWe.poke(false.B)

      // Initialize IMEM
      c.io.imemWe.poke(true.B)
      for (i <- imem.indices) {
         if (imem(i) != 0) {
             c.io.imemWaddr.poke(i.U)
             c.io.imemWdata.poke((imem(i).toLong & 0xFFFFFFFFL).U)
             c.clock.step(1)
         }
      }
      c.io.imemWe.poke(false.B)

      // Initialize DMEM
      c.io.dmemWe.poke(true.B)
      for (i <- 0 until copyLen) {
           c.io.dmemWaddr.poke(i.U)
           c.io.dmemWdata.poke((newDmem(i).toLong & 0xFFFFFFFFL).U)
           c.clock.step(1)
      }
      c.io.dmemWe.poke(false.B)

      // Run Simulation
      c.io.run.poke(true.B)
      
      // The benchmark takes a while, so we need a generous timeout. 
      // The prime calculation loop runs for 25 primes.
      // Based on typical instruction counts, 200,000 cycles might be enough given the simplicity.
      // Or we can just let it run until done with a very high timeout.
      c.clock.setTimeout(50000000) 
      
      var cycles = 0
      while (!c.io.done.peek().litToBoolean) {
         c.clock.step(1)
         cycles += 1
         
         // Optional: Print cycle count periodically
         if (cycles % 10000 == 0) {
           println(s"Cycle: $cycles")
         }
      }
      println(s"Prime Benchmark took $cycles cycles")

      // Check registers? 
      // The code returns 0 in main, which goes to x10 usually? 
      // But main returns to entry which does ecall.
      // We can inspect register file if we want to debug, but just finishing is a success.
    }
  }
}
