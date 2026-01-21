.globl _start

_start:
    # ----------------------------------------------------
    # 1. SETUP & LOAD PHASES
    # ----------------------------------------------------
    li s0, 0x20000000    # s0 holds the base address 0x2000_0000
    li s1, 4            # s1 is our Loop Counter (4 pairs)

    # --- Load Pair 1 (Keep in Registers x10, x11 for first run) ---
    li x10, 1000000     # Pair 1 Value A
    li x11, 3540094     # Pair 1 Value B

    # --- Load Pair 2 (Save to Mem Offset 0x08) ---
    li t0, 500          # Pair 2 Value A
    li t1, 250          # Pair 2 Value B
    sw t0, 8(s0)        # Store A at 0x2000_0008
    sw t1, 12(s0)       # Store B at 0x2000_000C

    # --- Load Pair 3 (Save to Mem Offset 0x10) ---
    li t0, 27           # Pair 3 Value A
    li t1, 81           # Pair 3 Value B
    sw t0, 16(s0)       # Store A at 0x2000_0010
    sw t1, 20(s0)       # Store B at 0x2000_0014

    # --- Load Pair 4 (Save to Mem Offset 0x18) ---
    li t0, 12           # Pair 4 Value A
    li t1, 16           # Pair 4 Value B
    sw t0, 24(s0)       # Store A at 0x2000_0018
    sw t1, 28(s0)       # Store B at 0x2000_001C
    
    # Keep a pointer to the current pair being processed
    mv s2, s0           # s2 will act as our moving pointer

    # ----------------------------------------------------
    # 2. MAIN LOOP
    # ----------------------------------------------------
process_loop:
    # At this point, x10 and x11 contain the numbers to calculate

gcd_start:
    # --- GCD ALGORITHM (Euclidean Subtraction) ---
    beq x10, x11, gcd_done
    blt x10, x11, swap_sub
    sub x10, x10, x11
    j gcd_start

swap_sub: 
    sub x11, x11, x10
    j gcd_start

gcd_done:
    # Result is now in x10
    
    # --- WRITE RESULT TO MEMORY ---
    sw x10, 0(s2)       # Store GCD result 
    sw x0,  4(s2)       # Store Zero

    # --- PREPARE FOR NEXT LOOP ---
    addi s1, s1, -1     # Decrement counter
    beq s1, x0, program_end

    # --- FETCH NEXT PAIR ---
    addi s2, s2, 8      # Move pointer
    lw x10, 0(s2)       # Load next Value A
    lw x11, 4(s2)       # Load next Value B
    j process_loop

program_end:  
    ecall