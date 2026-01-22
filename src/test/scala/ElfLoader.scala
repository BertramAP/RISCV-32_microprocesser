package stages

import java.io.File
import net.fornwall.jelf.ElfFile

object ElfLoader {
  private def byteToWord(byteArray: Array[Byte]): Array[Int] = {
    val arr = new Array[Int](math.max(1, byteArray.length / 4))
    if (byteArray.isEmpty) {
      arr(0) = 0
      return arr
    }

    for (i <- 0 until byteArray.length / 4) {
      var word = 0
      for (j <- 0 until 4) {
        word >>>= 8
        word += (byteArray(i * 4 + j).toInt & 0xff) << 24
      }
      arr(i) = word
    }
    arr
  }

  def readBin(fileName: String): Array[Int] = {
    val byteArray = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(fileName))
    byteToWord(byteArray)
  }

  def readRes(fileName: String): Array[Int] = {
    readBin(fileName)
  }

  def readHex(fileName: String): Array[Int] = {
    val source = scala.io.Source.fromFile(fileName)
    try {
      val lines = source.getLines().toArray
      val length = lines.length
      val arr = new Array[Int](length * 4)
      var i = 0
      for (l <- lines) {
        for (j <- 0 until 4) {
          val s = l.substring((3 - j) * 8, (3 - j) * 8 + 8)
          arr(i * 4 + j) = java.lang.Long.parseLong(s, 16).toInt
        }
        i += 1
      }
      arr
    } finally {
      source.close()
    }
  }

  def getAsmFiles(path: String = "asm", ext: String = ".s"): List[String] = {
    val d = new File(path)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList.filter(_.getName.endsWith(ext)).map(_.toString)
    } else {
      List[String]()
    }
  }

 def load(fileName: String): (Array[Int], Array[Int], Int) = {
    /*if (fileName.endsWith(".bin")) {
      val bins = readBin(fileName)
      (bins, bins, 0)
    } else if (fileName.endsWith(".hex")) {
      val hexs = readHex(fileName)
      (hexs, hexs, 0x200)
    } else {*/
      val exe = lib.Executable.from(new File(fileName))
      val sections = exe.getAllLoadableSections()
      
      if (sections.isEmpty) throw new Exception(s"No loadable sections in $fileName")

      // Find the absolute bounds of the program in memory
      val minAddr = sections.map(_.start).min
      val maxAddr = sections.map(_.end).max
      val sizeInWords = (((maxAddr - minAddr) + 3) / 4).toInt

      val memory = new Array[Int](sizeInWords)

      for (s <- sections) {
        if (s.name != ".comment" && s.name != ".riscv.attributes" && s.name != ".note.gnu.build-id") {
          val wordOffset = ((s.start - minAddr) / 4).toInt
        val words = s.getWords
        for (i <- words.indices) {
          if (wordOffset + i < memory.length) {
            memory(wordOffset + i) = words(i).toInt
          }
        }
      }
    }

      // Entry point must be relative to our memory array base
      val relativeEntry = (exe.getEntryPoint - minAddr).toInt
      
      // In RV32I, imem and dmem usually share the same address space
      (memory, memory, relativeEntry & ~3)
    }
  }
//}
