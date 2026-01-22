
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.ElfLoader
import stages.BenteTop
import java.nio.file.Paths

class PrimeTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Prime Benchmark"

  it should "run prime.bin until completion" in {
    val elfFile = "tests/Prime_benchmark/prime.elf"
    if (!new java.io.File(elfFile).exists) {
        cancel(s"Binary file $elfFile not found")
    }
    val (imem, dmem, start) = ElfLoader.load(elfFile)

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
      
      c.clock.setTimeout(50000000) 
      
      var cycles = 0
      while (!c.io.done.peek().litToBoolean) {
         c.clock.step(1)
         cycles += 1
      }
      println(s"Prime Benchmark took $cycles cycles")
    }
  }
}
