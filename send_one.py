import serial, time

PORT = "COM6"
#PORT = "/dev/ttyUSB1"
BAUD = 115200

IMEM = [0x00100093, 0x00000073]  # addi x1,1 ; ecall
DMEM = [0x00000000]

def send_block(ser, mem_used, words):
    payload = b"".join((w & 0xFFFFFFFF).to_bytes(4, "little") for w in words)
    frame = bytes([mem_used]) + len(payload).to_bytes(3, "little") + payload
    ser.write(frame)
    ser.flush()

with serial.Serial(PORT, BAUD, timeout=1) as ser:
    time.sleep(0.2)
    send_block(ser, 0, IMEM)  # 0 = IMEM
    send_block(ser, 1, DMEM)  # 1 = DMEM
