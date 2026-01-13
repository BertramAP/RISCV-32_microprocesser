import serial
import time
import struct
from elftools.elf.elffile import ELFFile

PORT = '/dev/ttyUSB1'  # Change this to your UART port
BAUDRATE = 115200 # Shloud be the same for all of us, so dont change it
# Use elf file for meta data
FILE = "/home/ap/Dokumenter/RISCV-32_microprocesser/tests/ripes/add.out"  # Change this to the elf file path you want to upload

def upload_firmware(port, baudrate, file_path):
    ser = serial.Serial(port, baudrate, timeout=1)
    print(f"Opened port {port} at {baudrate} baud.")
    with open(file_path, 'rb') as f:
        elf = ELFFile(f)
        text_section = elf.get_section_by_name('.text')
        if text_section:
            print(f"fount .text section")
            text_data = text_section.data()
            send_packet(ser, packet_type=0x00, data=text_data)
        else:
            print(f".text section not found in ELF file.")
        
        data_section = elf.get_section_by_name('.data')
        if data_section:
            print(f"fount .data section")
            data_data = data_section.data()
            send_packet(ser, packet_type=0x01, data=data_data)
        else:
            print(f".data section not found in ELF file.")
    

    print("Upload complete.")
    ser.close()

def send_packet(ser, packet_type, data):
    length = len(data)
    header = struct.pack("<BI", packet_type, length) # < for little-endian, B for unsigned char, I for unsigned int (4 bytes)

    print(f"sending header: type={packet_type} with length {length}")
    ser.write(header)

    ser.write(data)
    time.sleep(0.01)  # Small delay to ensure data is sent properly


upload_firmware(PORT, BAUDRATE, FILE)