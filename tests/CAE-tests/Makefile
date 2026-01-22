
# Toolchain
CC = riscv64-unknown-elf-gcc
OBJCOPY = riscv64-unknown-elf-objcopy
OBJDUMP = riscv64-unknown-elf-objdump

# Flags
CFLAGS = -march=rv32i -mabi=ilp32 -nostdlib -Wl,-T,../simple/linker.ld
OUTDIR = ../../build/CAE-tests

# Finding sources
TASKS = task1 task2 task3 task4
SOURCES_S = $(foreach task,$(TASKS),$(wildcard $(task)/*.s))
SOURCES_C = $(foreach task,$(TASKS),$(wildcard $(task)/*.c))
# Filter out C files that have corresponding S files
SOURCES_C_FILTERED = $(filter-out $(SOURCES_S:.s=.c), $(SOURCES_C))

OBJECTS_S = $(patsubst %.s,$(OUTDIR)/%.out,$(SOURCES_S))
OBJECTS_C = $(patsubst %.c,$(OUTDIR)/%.out,$(SOURCES_C_FILTERED))

OBJECTS = $(OBJECTS_S) $(OBJECTS_C)
BINARIES = $(patsubst %.out,%.bin,$(OBJECTS))

.PHONY: all clean directories

all: directories $(BINARIES)

directories:
	@mkdir -p $(foreach task,$(TASKS),$(OUTDIR)/$(task))

# Rule to compile .s to .out (ELF)
$(OUTDIR)/%.out: %.s
	$(CC) $(CFLAGS) -o $@ $<

# Rule to compile .c to .out (ELF)
$(OUTDIR)/%.out: %.c
	$(CC) $(CFLAGS) -o $@ $<

# Rule to convert .out to .bin (Binary)
$(OUTDIR)/%.bin: $(OUTDIR)/%.out
	$(OBJCOPY) -O binary $< $@

clean:
	rm -rf $(OUTDIR)

.SECONDARY: