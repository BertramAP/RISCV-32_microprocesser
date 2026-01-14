#!/usr/bin/env python3
import argparse
import serial
import time

DEFAULT_PORT = "COM6"
DEFAULT_BAUD = 115200

# Tiny "proof of life" program:
#   addi x1, x0, 1   -> your core.io.led should turn ON (x1==1)
#   ecall            -> your pipeline done bit should go high (if Decode marks ECALL as done)
IMEM_WORDS_DEFAULT = [
    0x00100093,  # addi x1, x0, 1
    0x00000073,  # ecall
]

DMEM_WORDS_DEFAULT = [
    0x00000000,  # one word is enough to mark dmemLoaded
]

RAW_PATTERN_DEFAULT = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80, 0x55, 0xAA, 0xFF, 0x00]


def words_to_payload_le(words):
    """Pack 32-bit words into little-endian bytes (LSB first)."""
    payload = bytearray()
    for w in words:
        payload += (w & 0xFFFFFFFF).to_bytes(4, "little", signed=False)
    return payload


def send_block(ser, mem_used, payload, byte_delay=0.0):
    """
    Frame = [memUsed:1] + [len:3 little-endian] + [payload bytes]
    mem_used: 0=IMEM, 1=DMEM
    """
    if mem_used not in (0, 1):
        raise ValueError("mem_used must be 0 (IMEM) or 1 (DMEM)")
    if len(payload) >= (1 << 24):
        raise ValueError("payload too large for 24-bit length")

    frame = bytearray()
    frame.append(mem_used)
    frame += len(payload).to_bytes(3, "little")
    frame += payload

    # Send byte-by-byte (very robust with simple UART loaders)
    for b in frame:
        ser.write(bytes([b]))
        ser.flush()
        if byte_delay > 0:
            time.sleep(byte_delay)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", default=DEFAULT_PORT)
    ap.add_argument("--baud", type=int, default=DEFAULT_BAUD)
    ap.add_argument("--byte-delay", type=float, default=0.0,
                    help="Optional delay between bytes (seconds). Try 0.0005 if you see overruns.")
    ap.add_argument("--raw-test", action="store_true",
                    help="Send a simple byte pattern (does NOT follow the memUsed/length protocol).")
    args = ap.parse_args()

    with serial.Serial(args.port, args.baud, timeout=1) as ser:
        time.sleep(0.2)

        if args.raw_test:
            print("RAW TEST: sending pattern bytes (no framing)...")
            for b in RAW_PATTERN_DEFAULT:
                ser.write(bytes([b]))
                ser.flush()
                time.sleep(0.3)
            print("RAW TEST done.")
            return

        # Build framed IMEM + DMEM blocks
        imem_payload = words_to_payload_le(IMEM_WORDS_DEFAULT)
        dmem_payload = words_to_payload_le(DMEM_WORDS_DEFAULT)

        print(f"Sending IMEM block: {len(IMEM_WORDS_DEFAULT)} words ({len(imem_payload)} bytes)")
        send_block(ser, mem_used=0, payload=imem_payload, byte_delay=args.byte_delay)

        print(f"Sending DMEM block: {len(DMEM_WORDS_DEFAULT)} words ({len(dmem_payload)} bytes)")
        send_block(ser, mem_used=1, payload=dmem_payload, byte_delay=args.byte_delay)

        print("Done sending.")
        print("Expected on board:")
        print("- 'loaded' LED turns on after both blocks are received")
        print("- press button to run")
        print("- x1 LED turns on (addi x1,1)")
        print("- done LED turns on after ECALL (if your core marks ECALL as done)")


if __name__ == "__main__":
    main()
