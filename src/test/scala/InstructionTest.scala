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

        test(new BenteTop(imem, newDmem, start)) { c =>
          c.clock.setTimeout(5000) 
          c.clock.step(1)
          var cycles = 0
          while (!c.io.done.peek().litToBoolean) {
             c.clock.step(1)
             cycles += 1
          }
          println(s"$testName from $testDir took $cycles cycles")

          assert(c.io.done.peek().litToBoolean, s"Simulation of $testName from $testDir timed out")

          if (testDir.contains("simple")) {
            // For simple tests, check x17 (a7) for success (10 = success)
            val exitCode = c.io.debug_regFile(17).peek().litValue
            assert(exitCode == 10, f"Test $testName from $testDir failed with $exitCode (x17), expected 10")
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

