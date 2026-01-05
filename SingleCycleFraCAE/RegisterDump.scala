import chisel3.iotesters.{Driver, PeekPokeTester}
import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.{Files, Path, Paths}

/** CLI runner to execute a program and dump registers x0-x31 in little-endian. */
object RegisterDump {
  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      Console.err.println("Usage: RegisterDump <program.bin> [output.res] [maxCycles]")
      sys.exit(1)
    }

    val programPath = Paths.get(args(0)).toAbsolutePath.normalize()
    val outputPath = if (args.length > 1) Paths.get(args(1)).toAbsolutePath.normalize()
    else programPath.resolveSibling(programPath.getFileName.toString + ".out")
    val maxCycles = if (args.length > 2) args(2).toInt else 200000

    require(Files.exists(programPath), s"Program does not exist: $programPath")

    val regs = new Array[Long](32)
    val success = Driver.execute(Array("--generate-vcd-output", "off"), () => new SingleCycleRiscV(Some(programPath.toString))) { c =>
      new PeekPokeTester(c) {
        var cycles = 0
        while (peek(dut.io.done) == BigInt(0) && cycles < maxCycles) {
          step(1)
          cycles += 1
        }
        if (peek(dut.io.done) == BigInt(0)) {
          require(requirement = false, s"Program did not finish within $maxCycles cycles")
        }
        for (i <- regs.indices) {
          regs(i) = (peek(dut.io.regState(i)) & 0xffffffffL).toLong
        }
      }
    }

    if (!success) {
      Console.err.println("Simulation failed")
      sys.exit(1)
    }

    writeRegisters(outputPath, regs)
    println(s"Wrote register dump to $outputPath")
  }

  private def writeRegisters(path: Path, regs: Array[Long]): Unit = {
    val bos = new BufferedOutputStream(new FileOutputStream(path.toFile))
    try {
      val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
      regs.foreach { r =>
        bb.clear()
        bb.putInt(r.toInt)
        bos.write(bb.array())
      }
    } finally {
      bos.close()
    }
  }
}
