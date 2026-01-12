import serial
import time
import sys

PORT = '/dev/ttyUSB0'  # Change this to your UART port
BAUDRATE = 115200 # Shloud be the same for all of us, so dont change it
FILE = "/home/ap/Dokumenter/RISCV-32_microprocesser/tests/ripes/add.bin"  # Change this to the binary file path you want to upload

def upload_firmware(port, baudrate, file_path):
    ser = serial.Serial(port, baudrate, timeout=1)
    print(f"Opened port {port} at {baudrate} baud.")
    with open(file_path, 'rb') as f:
        byte = f.read(1)

        while byte:
            ser.write(byte)
            time.sleep(0.01)  # Small delay to ensure data is sent properly
            byte = f.read(1)
    

    print("Upload complete.")
    ser.close()

upload_firmware(PORT, BAUDRATE, FILE)