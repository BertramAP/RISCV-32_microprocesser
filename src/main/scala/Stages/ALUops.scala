package stages

import chisel3._
import chisel3.util._

object ALUops {
    val ALU_ADD  = 0.U(4.W) // Addition operation
    val ALU_SUB  = 1.U(4.W) // Subtraction operation
    val ALU_AND = 2.U(4.W) // AND operation
    val ALU_OR  = 3.U(4.W) // OR operation
    val ALU_XOR = 4.U(4.W) // XOR operation
    val ALU_SLT = 5.U(4.W) // Set Less Than operation
    val ALU_SLL = 6.U(4.W) // Shift Left Logical operation
    val ALU_SRL = 7.U(4.W) // Shift Right Logical operation
    val ALU_SRA = 8.U(4.W) // Shift Right Arithmetic operation
    val ALU_SLTU = 9.U(4.W) // Set Less Than Unsigned operation
}