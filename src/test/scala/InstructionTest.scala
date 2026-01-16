import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.ElfLoader
import stages.BenteTop
import scala.io.Source
import java.nio.file.{Files, Paths}

// Test one test or directory with sbt "testOnly InstructionTest -- -z name"

class InstructionTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Bente"
  val testDirs = Seq("build/ripes", "build/riscv-tests", "build/simple")

  testDirs.foreach { testDir =>
    val instructionTests = ElfLoader.getAsmFiles(testDir, ".out")

    instructionTests.foreach { testFile =>
      val testName = Paths.get(testFile).getFileName.toString.stripSuffix(".out")
      val binFile = Paths.get(testDir, s"$testName.out").toString
      val resFile = Paths.get(testDir, s"$testName.res").toString

      it should s"pass the $testName test from $testDir" in {
        // Use ElfLoader for all supported file types
        val (imem, dmem, start) = ElfLoader.load(binFile)

        val dmemSize = 4096
        val newDmem = new Array[Int](dmemSize)
        Array.copy(dmem, 0, newDmem, 0, dmem.length)

        test(new BenteTop(imem, newDmem, start, dmemSize)) { c =>
          // Explicitly initialize inputs
          c.io.run.poke(false.B)
          c.io.imemWe.poke(false.B)
          c.io.dmemWe.poke(false.B)

          // Initialize IMEM
          c.io.imemWe.poke(true.B)
          for (i <- imem.indices) {
             c.io.imemWaddr.poke(i.U)
             c.io.imemWdata.poke((imem(i).toLong & 0xFFFFFFFFL).U)
             c.clock.step(1)
          }
          c.io.imemWe.poke(false.B)

          // Initialize DMEM
          c.io.dmemWe.poke(true.B)
          for (i <- newDmem.indices) {
             c.io.dmemWaddr.poke(i.U)
             c.io.dmemWdata.poke((newDmem(i).toLong & 0xFFFFFFFFL).U)
             c.clock.step(1) // Step to write
          }
          c.io.dmemWe.poke(false.B)

          // Run Simulation
          c.io.run.poke(true.B)
          c.clock.setTimeout(5000) 
          
          var cycles = 0
          while (!c.io.done.peek().litToBoolean) {
             c.clock.step(1)
             cycles += 1
          }
           println(s"$testName from $testDir took $cycles cycles")

          assert(c.io.done.peek().litToBoolean, s"Simulation of $testName from $testDir timed out")

          if (testDir.contains("simple")) {
            // For simple tests, compare against .res file in tests/simple
            val resPath = Paths.get("tests", "simple", s"$testName.res").toString
            if (Files.exists(Paths.get(resPath))) {
              val expectedRegs = ElfLoader.readRes(resPath)
              for (i <- 0 until 32) {
                val actual = c.io.debug_regFile(i).peek().litValue.toInt
                val expected = expectedRegs(i)
                assert(actual == expected, f"Test $testName failed at register x$i. Actual: $actual%08x, Expected: $expected%08x")
              }
            } else {
               // Fallback if .res file is missing (though it should be there)
               val exitCode = c.io.debug_regFile(17).peek().litValue
               assert(exitCode == 10, f"Test $testName from $testDir didnt finish with $exitCode (x17), expected 10")
            }

          } else {
            // For riscv-tests and ripes, check x10 (a0) for success (0 = success) and x17 (a7) for exit code (93 = made it to the end)
            val success = c.io.debug_regFile(10).peek().litValue
            val exitCode = c.io.debug_regFile(17).peek().litValue
            assert(exitCode == 93 && success == 0, f"Test $testName from $testDir failed with $exitCode (x10)")
          }
        }
      }
    }
  }
}

