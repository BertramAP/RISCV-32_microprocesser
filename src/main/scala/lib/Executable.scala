package lib

import net.fornwall.jelf.ElfFile
import java.io.File

class Executable(val name: String, val elf: ElfFile) {
  
  // Gets a specific section by name
  def getSection(name: String): Option[Executable.Section] = {
    val section = elf.firstSectionByName(name)
    if (section == null) None
    else Some(Executable.Section(section.header.sh_addr & 0xffffffffL, section.getData))
  }

  // Gets all sections that should be loaded into memory (Progbits)
  def getAllLoadableSections(): List[Executable.Section] = {
    val sections = scala.collection.mutable.ListBuffer[Executable.Section]()
    for (i <- 0 until elf.e_shnum) {
      val sh = elf.getSection(i)
      // 1 = SHT_PROGBITS (data/code), 8 = SHT_NOBITS (bss)
      if (sh.header.sh_type == 1 || sh.header.sh_type == 8) {
        sections += Executable.Section(sh.header.sh_addr & 0xffffffffL, sh.getData)
      }
    }
    sections.toList
  }

  def getEntryPoint: Long = elf.e_entry & 0xffffffffL
}

object Executable {
  case class Section(start: Long, data: Array[Byte]) {
    val end: Long = start + data.length
    def getWords: Seq[Long] = {
      if (data == null) return Seq.fill(0)(0L)
      data.grouped(4).map { group =>
        var word = 0L
        for (i <- group.indices) {
          word |= (group(i).toLong & 0xff) << (8 * i)
        }
        word
      }.toSeq
    }
  }

  def from(file: File): Executable = {
    val elf = ElfFile.from(file)
    // 0xf3 is EM_RISCV
    if (!elf.is32Bits() || elf.e_machine != 0xf3) throw new Exception("Not a RV32I executable")
    new Executable(file.getName, elf)
  }
}