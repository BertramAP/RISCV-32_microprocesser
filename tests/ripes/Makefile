OUTDIR=.
# Define the tools explicitly
CC = riscv64-unknown-elf-gcc
OBJCOPY = riscv64-unknown-elf-objcopy

SRCS:=$(wildcard *.s)
OBJ:=$(SRCS:%.s=$(OUTDIR)/%.out)
BINS:=$(OBJ:%.out=%.bin)

all: ${OBJ} ${BINS}

# Rule to compile assembly (.s) to object file (.out)
# Notice we just use $(CC), we do not add "-gcc"
$(OUTDIR)/%.out: %.s
	$(CC) -march=rv32i -mabi=ilp32 -ffreestanding -nostartfiles -nostdlib -nodefaultlibs -Wl,-T,linker.ld -o $@ $<

# Rule to convert object file (.out) to binary (.bin)
# Notice we use the new $(OBJCOPY) variable
$(OUTDIR)/%.bin: $(OUTDIR)/%.out
	$(OBJCOPY) -O binary $< $@

clean:
	@rm -f $(OUTDIR)/*.bin $(OUTDIR)/*.out