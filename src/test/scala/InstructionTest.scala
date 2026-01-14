import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import stages.ElfLoader
import stages.BenteTop
import scala.io.Source
import java.nio.file.{Files, Paths}

class InstructionTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Bente"
  val testDirs = Seq("tests/ripes", "tests/riscv-tests", "tests/simple")

  testDirs.foreach { testDir =>
    val instructionTests = ElfLoader.getAsmFiles(testDir, ".s")

    instructionTests.foreach { testFile =>
      val testName = Paths.get(testFile).getFileName.toString.stripSuffix(".s")
      val binFile = Paths.get(testDir, s"$testName.out").toString
      val resFile = Paths.get(testDir, s"$testName.res").toString

      it should s"pass the $testName test from $testDir" in {
        // Use ElfLoader for all supported file types
        val (imem, dmem, start) = ElfLoader.load(binFile)

        val dmemSize = 4096
        val newDmem = new Array[Int](dmemSize)
        Array.copy(dmem, 0, newDmem, 0, dmem.length)

        println(f"Starting simulation of $testName at PC=0x$start%x") // Debug start address

        test(new BenteTop(imem, newDmem, start)) { c =>
          c.clock.setTimeout(5000) // Max 5000, so aprox 3 minutes max
          c.clock.step(1) 
          while (!c.io.done.peek().litToBoolean) {
            c.clock.step(1)
          }

          assert(c.io.done.peek().litToBoolean, s"Simulation of $testName from $testDir timed out")

          if (Files.exists(Paths.get(resFile))) {
            val expectedRegisters = ElfLoader.readRes(resFile)
            for (i <- 1 until 32) {
              val expected = (expectedRegisters(i).toLong & 0xFFFFFFFFL)
              val actual = c.io.debug_regFile(i).peek().litValue
              assert(actual == expected, f"Register x$i failed test $testName from $testDir: expected 0x$expected%08x, got 0x$actual%08x")
            }
          }
        }
      }
    }
  }
}
