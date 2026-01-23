# Makefile for the GCD benchmark

# Default configuration (adjust if needed)
UART_ADDR ?= 0x00001000

# Toolchain selection – adjust CROSS if your toolchain uses a different prefix
UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
  export CROSS=riscv64-elf-
else
  export CROSS=riscv64-unknown-elf-
endif

CC = riscv64-elf-gcc
OBJCOPY = riscv64-elf-objcopy

# Compiler flags for a bare‑metal RV32I target
CFLAGS = -mabi=ilp32 -march=rv32i -O0 -ffreestanding -nostdlib -e entry -T linker.ld

TARGET = gcd
SRC = GCD.s

.PHONY: all clean

all: $(TARGET).bin

$(TARGET).elf: $(SRC)
	$(CC) $(CFLAGS) -o $@ $<

$(TARGET).bin: $(TARGET).elf
	$(OBJCOPY) -O binary $< $@

clean:
	rm -f *.elf *.bin
