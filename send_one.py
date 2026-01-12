import serial, time

PORT = "/dev/ttyUSB1"    
BAUD = 115200

with serial.Serial(PORT,115200) as ser:
    time.sleep(0.2)
    for b in [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80, 0x55, 0xAA, 0xFF, 0x00]:
        ser.write(bytes([b]))
        ser.flush()
        time.sleep(0.3)


