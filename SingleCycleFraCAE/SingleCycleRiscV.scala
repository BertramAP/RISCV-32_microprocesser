import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

/** Single-cycle RV32I processor with byte-addressable unified memory. */
class SingleCycleRiscV(
    programPath: Option[String] = None,
    memSizeBytes: Int = SingleCycleRiscV.DefaultMemSizeBytes) extends Module {
  val io = IO(new Bundle {
    val regDeb = Output(Vec(4, UInt(32.W)))
    val regState = Output(Vec(32, UInt(32.W)))
    val done = Output(Bool())
  })

  require(memSizeBytes % 4 == 0, "Memory size must be a multiple of four bytes.")

  private val programBytes = SingleCycleRiscV.loadProgramBytes(programPath)
  private val effectiveMemSize = memSizeBytes
  private val truncatedProgram =
    if (programBytes.length > effectiveMemSize) programBytes.take(effectiveMemSize) else programBytes
  private val programLength = truncatedProgram.length
  private val memWordCount = effectiveMemSize / 4

  require(memWordCount > 0, "The memory must contain at least one 32-bit word.")

  private val initialWordData = SingleCycleRiscV.bytesToWords(truncatedProgram, memWordCount)

  val memory = Mem(memWordCount, UInt(32.W))
  SingleCycleRiscV.preloadMemory(memory, initialWordData)
  val registers = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val pc = RegInit(0.U(32.W))
  val running = RegInit(true.B)

  private val memWordAddrWidth = log2Ceil(memWordCount)
  private val programSizeBytesLit = BigInt(programLength).U(32.W)
  private val memSizeBytesLit = BigInt(effectiveMemSize).U(32.W)

  private def wordIndex(addr: UInt): UInt =
    if (memWordAddrWidth == 0) 0.U else addr(memWordAddrWidth + 1, 2)

  private def byteShift(addr: UInt): UInt = Cat(addr(1, 0), 0.U(3.W))

  // --- Stage bundles (single-cycle datapath stitched into clear phases) ---
  class FetchStage extends Bundle {
    val pc = UInt(32.W)
    val pcPlus4 = UInt(32.W)
    val instruction = UInt(32.W)
  }

  class DecodeStage extends Bundle {
    val opcode = UInt(7.W)
    val rd = UInt(5.W)
    val funct3 = UInt(3.W)
    val rs1 = UInt(5.W)
    val rs2 = UInt(5.W)
    val funct7 = UInt(7.W)
    val immI = UInt(32.W)
    val immS = UInt(32.W)
    val immB = UInt(32.W)
    val immU = UInt(32.W)
    val immJ = UInt(32.W)
    val immIS = SInt(32.W)
    val immSS = SInt(32.W)
    val immBS = SInt(32.W)
    val immJS = SInt(32.W)
    val rs1Val = UInt(32.W)
    val rs2Val = UInt(32.W)
    val shamtImm = UInt(5.W)
    val pcPlus4 = UInt(32.W)
    val pc = UInt(32.W)
  }

  class ExecuteStage extends Bundle {
    val nextPc = UInt(32.W)
    val regWriteEnable = Bool()
    val regWriteAddr = UInt(5.W)
    val regWriteData = UInt(32.W)
    val memWriteEnable = Bool()
    val memWriteAddr = UInt(32.W)
    val memWriteMask = UInt(4.W)
    val memWriteData = UInt(32.W)
    val halt = Bool()
    val trap = Bool()
  }

  // Stage wires with defaults to keep the subsequent logic organized.
  val fetch = Wire(new FetchStage)
  val decode = Wire(new DecodeStage)
  val exec = Wire(new ExecuteStage)

  fetch.pc := pc
  fetch.pcPlus4 := (pc + 4.U)(31, 0)
  fetch.instruction := memory.read(wordIndex(pc))

  decode.opcode := fetch.instruction(6, 0)
  decode.rd := fetch.instruction(11, 7)
  decode.funct3 := fetch.instruction(14, 12)
  decode.rs1 := fetch.instruction(19, 15)
  decode.rs2 := fetch.instruction(24, 20)
  decode.funct7 := fetch.instruction(31, 25)
  decode.immI := Cat(Fill(20, fetch.instruction(31)), fetch.instruction(31, 20))
  decode.immS := Cat(Fill(20, fetch.instruction(31)), fetch.instruction(31, 25), fetch.instruction(11, 7))
  decode.immB := Cat(Fill(19, fetch.instruction(31)), fetch.instruction(31), fetch.instruction(7), fetch.instruction(30, 25), fetch.instruction(11, 8), 0.U(1.W))
  decode.immU := Cat(fetch.instruction(31, 12), 0.U(12.W))
  decode.immJ := Cat(Fill(11, fetch.instruction(31)), fetch.instruction(31), fetch.instruction(19, 12), fetch.instruction(20), fetch.instruction(30, 21), 0.U(1.W))
  decode.immIS := decode.immI.asSInt
  decode.immSS := decode.immS.asSInt
  decode.immBS := decode.immB.asSInt
  decode.immJS := decode.immJ.asSInt
  decode.rs1Val := registers(decode.rs1)
  decode.rs2Val := registers(decode.rs2)
  decode.shamtImm := fetch.instruction(24, 20)
  decode.pcPlus4 := fetch.pcPlus4
  decode.pc := fetch.pc

  exec.nextPc := decode.pcPlus4
  exec.regWriteEnable := false.B
  exec.regWriteAddr := decode.rd
  exec.regWriteData := 0.U
  exec.memWriteEnable := false.B
  exec.memWriteAddr := 0.U
  exec.memWriteMask := 0.U
  exec.memWriteData := 0.U
  exec.halt := false.B
  exec.trap := false.B

  when(running) {
    // --- Fetch guard rails ---
    when(pc >= programSizeBytesLit) {
      exec.halt := true.B
    }.elsewhen(pc(1, 0) =/= 0.U) {
      exec.trap := true.B
    }.otherwise {
      val opcodeMatched = WireDefault(false.B)

      // --- Decode/Execute ---
      switch(decode.opcode) {
        is("b0110011".U) { // R-type
          opcodeMatched := true.B
          exec.regWriteEnable := true.B
          val shiftAmount = decode.rs2Val(4, 0)
          val rResult = WireDefault(0.U(32.W))
          val rValid = WireDefault(false.B)
          switch(decode.funct3) {
            is("b000".U) {
              val isSub = decode.funct7 === "b0100000".U
              val addVal = (decode.rs1Val.asSInt + decode.rs2Val.asSInt).asUInt
              val subVal = (decode.rs1Val.asSInt - decode.rs2Val.asSInt).asUInt
              rResult := Mux(isSub, subVal, addVal)
              rValid := true.B
            }
            is("b001".U) {
              rResult := (decode.rs1Val << shiftAmount)(31, 0)
              rValid := true.B
            }
            is("b010".U) {
              rResult := Mux(decode.rs1Val.asSInt < decode.rs2Val.asSInt, 1.U, 0.U)
              rValid := true.B
            }
            is("b011".U) {
              rResult := Mux(decode.rs1Val < decode.rs2Val, 1.U, 0.U)
              rValid := true.B
            }
            is("b100".U) {
              rResult := decode.rs1Val ^ decode.rs2Val
              rValid := true.B
            }
            is("b101".U) {
              val isSra = decode.funct7 === "b0100000".U
              rResult := Mux(isSra, (decode.rs1Val.asSInt >> shiftAmount).asUInt, (decode.rs1Val >> shiftAmount))
              rValid := true.B
            }
            is("b110".U) {
              rResult := decode.rs1Val | decode.rs2Val
              rValid := true.B
            }
            is("b111".U) {
              rResult := decode.rs1Val & decode.rs2Val
              rValid := true.B
            }
          }
          when(!rValid) {
            exec.trap := true.B
          }
          exec.regWriteData := rResult
        }

        is("b0010011".U) { // I-type ALU
          opcodeMatched := true.B
          exec.regWriteEnable := true.B
          val iValid = WireDefault(false.B)
          switch(decode.funct3) {
            is("b000".U) {
              exec.regWriteData := (decode.rs1Val.asSInt + decode.immIS).asUInt
              iValid := true.B
            }
            is("b010".U) {
              exec.regWriteData := Mux(decode.rs1Val.asSInt < decode.immIS, 1.U, 0.U)
              iValid := true.B
            }
            is("b011".U) {
              exec.regWriteData := Mux(decode.rs1Val < decode.immI, 1.U, 0.U)
              iValid := true.B
            }
            is("b100".U) {
              exec.regWriteData := decode.rs1Val ^ decode.immI
              iValid := true.B
            }
            is("b110".U) {
              exec.regWriteData := decode.rs1Val | decode.immI
              iValid := true.B
            }
            is("b111".U) {
              exec.regWriteData := decode.rs1Val & decode.immI
              iValid := true.B
            }
            is("b001".U) {
              when(decode.funct7 === "b0000000".U) {
                exec.regWriteData := (decode.rs1Val << decode.shamtImm)(31, 0)
                iValid := true.B
              }.otherwise {
                exec.trap := true.B
              }
            }
            is("b101".U) {
              val isSra = decode.funct7 === "b0100000".U
              val isSrl = decode.funct7 === "b0000000".U
              when(isSra || isSrl) {
                val shiftVal = decode.shamtImm(4, 0)
                exec.regWriteData := Mux(isSra, (decode.rs1Val.asSInt >> shiftVal).asUInt, (decode.rs1Val >> shiftVal))
                iValid := true.B
              }.otherwise {
                exec.trap := true.B
              }
            }
          }
          when(!iValid) {
            exec.trap := true.B
          }
        }

        is("b0000011".U) { // Loads
          opcodeMatched := true.B
          val loadAddr = (decode.rs1Val.asSInt + decode.immIS).asUInt
          val loadSize = WireDefault(4.U(3.W))
          val alignOk = WireDefault(true.B)
          val functOk = WireDefault(false.B)

          switch(decode.funct3) {
            is("b000".U) { loadSize := 1.U; functOk := true.B }
            is("b001".U) { loadSize := 2.U; alignOk := loadAddr(0) === 0.U; functOk := true.B }
            is("b010".U) { loadSize := 4.U; alignOk := loadAddr(1, 0) === 0.U; functOk := true.B }
            is("b100".U) { loadSize := 1.U; functOk := true.B }
            is("b101".U) { loadSize := 2.U; alignOk := loadAddr(0) === 0.U; functOk := true.B }
          }

          when(!functOk) {
            exec.trap := true.B
          }.otherwise {
            val loadSizeExt = Cat(0.U(29.W), loadSize)
            val loadEnd = Cat(0.U(1.W), loadAddr) + Cat(0.U(1.W), loadSizeExt)
            val inRange = loadEnd <= Cat(0.U(1.W), memSizeBytesLit)
            when(alignOk && inRange) {
              val word = memory.read(wordIndex(loadAddr))
              val byteSel = loadAddr(1, 0)
              val selectedByte = MuxLookup(byteSel, word(7, 0), Seq(
                1.U -> word(15, 8),
                2.U -> word(23, 16),
                3.U -> word(31, 24)
              ))
              val selectedHalf = Mux(loadAddr(1), word(31, 16), word(15, 0))
              exec.regWriteEnable := true.B
              exec.regWriteAddr := decode.rd
              switch(decode.funct3) {
                is("b000".U) { exec.regWriteData := Cat(Fill(24, selectedByte(7)), selectedByte) }
                is("b001".U) {
                  val half = selectedHalf
                  exec.regWriteData := Cat(Fill(16, half(15)), half)
                }
                is("b010".U) { exec.regWriteData := word }
                is("b100".U) { exec.regWriteData := Cat(0.U(24.W), selectedByte) }
                is("b101".U) {
                  val half = selectedHalf
                  exec.regWriteData := Cat(0.U(16.W), half)
                }
              }
            }.otherwise {
              exec.trap := true.B
            }
          }
        }

        is("b0100011".U) { // Stores
          opcodeMatched := true.B
          val storeAddr = (decode.rs1Val.asSInt + decode.immSS).asUInt
          val storeSize = WireDefault(4.U(3.W))
          val alignOk = WireDefault(true.B)
          val functOk = WireDefault(false.B)
          switch(decode.funct3) {
            is("b000".U) { storeSize := 1.U; functOk := true.B }
            is("b001".U) { storeSize := 2.U; alignOk := storeAddr(0) === 0.U; functOk := true.B }
            is("b010".U) { storeSize := 4.U; alignOk := storeAddr(1, 0) === 0.U; functOk := true.B }
          }

          when(!functOk) {
            exec.trap := true.B
          }.otherwise {
            val storeSizeExt = Cat(0.U(29.W), storeSize)
            val storeEnd = Cat(0.U(1.W), storeAddr) + Cat(0.U(1.W), storeSizeExt)
            val inRange = storeEnd <= Cat(0.U(1.W), memSizeBytesLit)
            when(alignOk && inRange) {
              exec.memWriteEnable := true.B
              exec.memWriteAddr := storeAddr
              val shiftAmount = byteShift(storeAddr)
              switch(decode.funct3) {
                is("b000".U) {
                  exec.memWriteMask := UIntToOH(storeAddr(1, 0), 4).asUInt
                  exec.memWriteData := (decode.rs2Val(7, 0) << shiftAmount)(31, 0)
                }
                is("b001".U) {
                  exec.memWriteMask := Mux(storeAddr(1), "b1100".U(4.W), "b0011".U(4.W))
                  exec.memWriteData := (decode.rs2Val(15, 0) << shiftAmount)(31, 0)
                }
                is("b010".U) {
                  exec.memWriteMask := "b1111".U(4.W)
                  exec.memWriteData := decode.rs2Val
                }
              }
            }.otherwise {
              exec.trap := true.B
            }
          }
        }

        is("b1100011".U) { // Branches
          opcodeMatched := true.B
          val branchTarget = (decode.pc.asSInt + decode.immBS).asUInt
          val takeBranch = WireDefault(false.B)
          val branchValid = WireDefault(false.B)
          switch(decode.funct3) {
            is("b000".U) { takeBranch := decode.rs1Val === decode.rs2Val; branchValid := true.B }
            is("b001".U) { takeBranch := decode.rs1Val =/= decode.rs2Val; branchValid := true.B }
            is("b100".U) { takeBranch := decode.rs1Val.asSInt < decode.rs2Val.asSInt; branchValid := true.B }
            is("b101".U) { takeBranch := decode.rs1Val.asSInt >= decode.rs2Val.asSInt; branchValid := true.B }
            is("b110".U) { takeBranch := decode.rs1Val < decode.rs2Val; branchValid := true.B }
            is("b111".U) { takeBranch := decode.rs1Val >= decode.rs2Val; branchValid := true.B }
          }
          when(!branchValid) {
            exec.trap := true.B
          }
          when(!exec.trap && takeBranch) {
            exec.nextPc := branchTarget(31, 0)
          }
        }

        is("b1101111".U) { // JAL
          opcodeMatched := true.B
          exec.regWriteEnable := true.B
          exec.regWriteData := decode.pcPlus4
          val jumpTarget = (decode.pc.asSInt + decode.immJS).asUInt
          exec.nextPc := jumpTarget(31, 0)
        }

        is("b1100111".U) { // JALR
          opcodeMatched := true.B
          when(decode.funct3 === "b000".U) {
            val target = ((decode.rs1Val.asSInt + decode.immIS).asUInt & ~1.U(32.W))
            exec.regWriteEnable := true.B
            exec.regWriteData := decode.pcPlus4
            exec.nextPc := target(31, 0)
          }.otherwise {
            exec.trap := true.B
          }
        }

        is("b0110111".U) { // LUI
          opcodeMatched := true.B
          exec.regWriteEnable := true.B
          exec.regWriteData := decode.immU
        }

        is("b0010111".U) { // AUIPC
          opcodeMatched := true.B
          exec.regWriteEnable := true.B
          exec.regWriteData := (decode.pc + decode.immU)(31, 0)
        }

        is("b0001111".U) { // FENCE
          opcodeMatched := true.B
          // fence treated as a NOP
        }

        is("b1110011".U) { // SYSTEM
          opcodeMatched := true.B
          when(fetch.instruction === "h00000073".U) {
            val syscall = registers(17)
            when(syscall === 10.U) {
              exec.halt := true.B
            }.elsewhen(syscall === 1.U) {
              printf(p"[riscv] ecall print_int a0=${registers(10)}\n")
            }.elsewhen(syscall === 11.U) {
              printf(p"[riscv] ecall print_char a0=${registers(10)(7, 0)}\n")
            }
          }.elsewhen(fetch.instruction === "h00100073".U) {
            exec.halt := true.B
          }.otherwise {
            exec.trap := true.B
          }
        }
      }

      when(!opcodeMatched) {
        exec.trap := true.B
      }
    }

    // --- Memory + Writeback commit ---
    when(exec.trap) {
      running := false.B
    }.elsewhen(exec.halt) {
      running := false.B
    }.otherwise {
      pc := exec.nextPc
      when(exec.regWriteEnable && exec.regWriteAddr =/= 0.U) {
        registers(exec.regWriteAddr) := exec.regWriteData
      }
      when(exec.memWriteEnable && exec.memWriteMask.orR) {
        val idx = wordIndex(exec.memWriteAddr)
        val mask32 = SingleCycleRiscV.maskFromBytes(exec.memWriteMask)
        val current = memory.read(idx)
        val updated = (current & ~mask32) | (exec.memWriteData & mask32)
        memory.write(idx, updated)
      }
    }
  }

  registers(0) := 0.U

  io.done := !running
  for (i <- 0 until io.regDeb.length) {
    io.regDeb(i) := registers(i)
  }
  for (i <- 0 until io.regState.length) {
    io.regState(i) := registers(i)
  }
}

object SingleCycleRiscV {
  val DefaultMemSizeBytes: Int = 1 << 20

  private val DefaultProgramWords: Seq[Int] = Seq(
    0x00200093, // addi x1, x0, 2
    0x00300113, // addi x2, x0, 3
    0x002081b3, // add  x3, x1, x2
    0x00a00893, // addi x17, x0, 10 (syscall number)
    0x00000513, // addi x10, x0, 0  (return value)
    0x00000073  // ecall
  )

  private lazy val DefaultProgramBytes: Array[Byte] = wordsToBytes(DefaultProgramWords)

  private val PropertyKeys = Seq("riscv.program", "riscv.bin", "program")
  private val EnvKeys = Seq("RISCV_PROGRAM", "RISCV_BIN", "PROGRAM")

  private val SearchRoots: Seq[Path] = Seq(
    Paths.get("."),
    Paths.get("tests"),
    Paths.get("tests/task1"),
    Paths.get("tests/task2"),
    Paths.get("tests/task3"),
    Paths.get("tests/task4")
  )

  def loadProgramBytes(explicitPath: Option[String]): Array[Byte] = {
    val candidates = (explicitPath.toSeq ++ PropertyKeys.flatMap(sys.props.get) ++ EnvKeys.flatMap(sys.env.get))
      .map(_.trim)
      .filter(_.nonEmpty)

    val resolved = candidates.view.flatMap(resolveCandidate).headOption

    resolved.map(path => Files.readAllBytes(path)).getOrElse(DefaultProgramBytes)
  }

  private def resolveCandidate(candidate: String): Option[Path] = {
    val candidatePath = Paths.get(candidate)
    if (candidatePath.isAbsolute) {
      if (Files.exists(candidatePath)) Some(candidatePath.toAbsolutePath.normalize()) else None
    } else {
      SearchRoots.view
        .map(_.resolve(candidate))
        .find(path => Files.exists(path))
        .map(_.toAbsolutePath.normalize())
    }
  }

  private def wordsToBytes(words: Seq[Int]): Array[Byte] = {
    val buffer = Array.ofDim[Byte](words.length * 4)
    var i = 0
    while (i < words.length) {
      val w = words(i)
      buffer(i * 4) = (w & 0xff).toByte
      buffer(i * 4 + 1) = ((w >>> 8) & 0xff).toByte
      buffer(i * 4 + 2) = ((w >>> 16) & 0xff).toByte
      buffer(i * 4 + 3) = ((w >>> 24) & 0xff).toByte
      i += 1
    }
    buffer
  }

  def bytesToWords(bytes: Array[Byte], wordCount: Int): Array[Int] = {
    val words = Array.fill(wordCount)(0)
    var idx = 0
    while (idx < wordCount) {
      val base = idx * 4
      var value = 0
      if (base < bytes.length) value |= (bytes(base) & 0xff)
      if (base + 1 < bytes.length) value |= (bytes(base + 1) & 0xff) << 8
      if (base + 2 < bytes.length) value |= (bytes(base + 2) & 0xff) << 16
      if (base + 3 < bytes.length) value |= (bytes(base + 3) & 0xff) << 24
      words(idx) = value
      idx += 1
    }
    words
  }

  def preloadMemory(mem: Mem[UInt], words: Array[Int]): Unit = {
    val path = writeMemoryFile(words)
    loadMemoryFromFile(mem, path)
  }

  private def writeMemoryFile(words: Array[Int]): String = {
    val path = Files.createTempFile("riscv_mem_", ".mem")
    val writer = Files.newBufferedWriter(path, StandardCharsets.US_ASCII)
    try {
      var idx = 0
      while (idx < words.length) {
        val value = words(idx) & 0xffffffffL
        writer.write(f"$value%08x")
        writer.newLine()
        idx += 1
      }
    } finally {
      writer.close()
    }
    path.toAbsolutePath.toString
  }

  def maskFromBytes(mask: UInt): UInt = {
    Cat(
      Fill(8, mask(3)),
      Fill(8, mask(2)),
      Fill(8, mask(1)),
      Fill(8, mask(0))
    )
  }
}
