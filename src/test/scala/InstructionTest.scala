import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wildcat.Util
import stages.BenteTop
import scala.io.Source
import java.nio.file.{Files, Paths}

class InstructionTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Bente"
  val testDirs = Seq("tests/ripes", /*"tests/riscv-tests",*/ "tests/simple")

  testDirs.foreach { testDir =>
    val instructionTests = Util.getAsmFiles(testDir, ".s")

    instructionTests.foreach { testFile =>
      val testName = Paths.get(testFile).getFileName.toString.stripSuffix(".s")
      val binFile = Paths.get(testDir, s"$testName.bin").toString
      val resFile = Paths.get(testDir, s"$testName.res").toString

      it should s"pass the $testName test from $testDir" in {
        val (program, start) = Util.getCode(binFile)

        test(new BenteTop(program, start)) { c =>
          c.clock.setTimeout(50000)
          c.clock.step(1) // to start the pipeline
          while (!c.io.done.peek().litToBoolean) {
            c.clock.step(1)
          }

          assert(c.io.done.peek().litToBoolean, s"Simulation of $testName from $testDir timed out")

          if (Files.exists(Paths.get(resFile))) {
            val expectedRegisters = Util.readRes(resFile)
            for (i <- 1 until 32) { // x0 is always 0
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
