
# Toolchain
CC = riscv64-unknown-elf-gcc
OBJCOPY = riscv64-unknown-elf-objcopy
OBJDUMP = riscv64-unknown-elf-objdump

# Flags
CFLAGS = -march=rv32i -mabi=ilp32 -nostdlib -Wl,-T,../simple/linker.ld
OUTDIR = ../../build/CAE-tests

# Finding sources
TASKS = task1 task2 task3 task4
SOURCES = $(foreach task,$(TASKS),$(wildcard $(task)/*.s))
# Convert source paths to output paths (e.g., task1/add.s -> ../../build/CAE-tests/task1/add.out)
OBJECTS = $(patsubst %.s,$(OUTDIR)/%.out,$(SOURCES))
BINARIES = $(patsubst %.s,$(OUTDIR)/%.bin,$(SOURCES))

.PHONY: all clean directories

all: directories $(BINARIES)

directories:
	@mkdir -p $(foreach task,$(TASKS),$(OUTDIR)/$(task))

# Rule to compile .s to .out (ELF)
$(OUTDIR)/%.out: %.s
	$(CC) $(CFLAGS) -o $@ $<

# Rule to convert .out to .bin (Binary)
$(OUTDIR)/%.bin: $(OUTDIR)/%.out
	$(OBJCOPY) -O binary $< $@

clean:
	rm -rf $(OUTDIR)

.SECONDARY: