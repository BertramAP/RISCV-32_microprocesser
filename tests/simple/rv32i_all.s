	.text
	.globl _start
_start:
	lui x1, 0x12345
	addi x2, x1, 0x678
	auipc x3, 0x0
	jal x4, after_jal
	addi x5, x0, 0x7
after_jal:
	addi x5, x0, 0
	addi x6, x0, 5
	addi x7, x0, 5
	beq x6, x7, beq_taken
	jal x0, beq_done
beq_taken:
	ori x5, x5, 0x001
beq_done:
	addi x6, x0, 5
	addi x7, x0, 6
	bne x6, x7, bne_taken
	jal x0, bne_done
bne_taken:
	ori x5, x5, 0x002
bne_done:
	addi x6, x0, -1
	addi x7, x0, 1
	blt x6, x7, blt_taken
	jal x0, blt_done
blt_taken:
	ori x5, x5, 0x004
blt_done:
	addi x6, x0, 2
	addi x7, x0, 2
	bge x6, x7, bge_taken
	jal x0, bge_done
bge_taken:
	ori x5, x5, 0x008
bge_done:
	addi x6, x0, -1
	addi x7, x0, 1
	bltu x6, x7, bltu_taken
	jal x0, bltu_done
bltu_taken:
	ori x5, x5, 0x010
bltu_done:
	addi x6, x0, -1
	addi x7, x0, 1
	bgeu x6, x7, bgeu_taken
	jal x0, bgeu_done
bgeu_taken:
	ori x5, x5, 0x020
bgeu_done:
	lui x6, %hi(mem_section)
	addi x6, x6, %lo(mem_section)
	jalr x0, x6, 0
	addi x5, x5, 0x7FF
mem_section:
	la x24, data
	lb x7, 0(x24)
	lbu x8, 0(x24)
	lh x9, 2(x24)
	lhu x11, 2(x24)
	lw x12, 8(x24)
	sw x0, 0x10(x24)
	addi x14, x0, 0x0AA
	sb x14, 0x10(x24)
	lui x15, 0x1
	addi x15, x15, 0x234
	sh x15, 0x12(x24)
	lw x13, 0x10(x24)

	addi x6, x0, 0x0F0
	slti x14, x6, 0x100
	sltiu x15, x6, 0x0F1
	xori x16, x6, 0x0FF
	ori x18, x6, 0x001
	andi x19, x6, 0x0F0
	slli x20, x6, 4
	srli x21, x6, 4
	srai x22, x6, 4

	add x23, x18, x16
	sub x24, x18, x16
	sll x25, x18, x16
	slt x26, x18, x16
	sltu x27, x18, x16
	xor x28, x18, x16
	srl x29, x18, x16
	sra x30, x18, x16
	or x31, x18, x16
	and x6, x18, x16

	addi x10, x0, 0
	addi x17, x0, 10
	ecall


	.section .data
data:
	.byte 0x80
	.byte 0x7F
	.half 0x8001
	.half 0x7FFF
	.balign 4
	.word 0xDEADBEEF
	.word 0x01020304
	.word 0x00000000
