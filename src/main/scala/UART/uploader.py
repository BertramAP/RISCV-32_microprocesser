import serial
import time
import struct
from elftools.elf.elffile import ELFFile

#PORT = '/dev/ttyUSB1'  # Change this to your UART port
PORT = 'COM6'  # Change this to your UART port
BAUDRATE = 115200 # Shloud be the same for all of us, so dont change it
# Use elf file for meta data
#FILE = "/home/ap/Dokumenter/RISCV-32_microprocesser/tests/ripes/and.out"  # Change this to the elf file path you want to upload
FILE = "C:/Users/Bertram/OneDrive - Danmarks Tekniske Universitet/RISCV-32_microprocesser/tests/ripes/add.out"  # Change this to the elf file path you want to upload
def uploadFirmware(ser, file_path):
    print(f"Opened port {ser.port} at {ser.baudrate} baud.")
    with open(file_path, 'rb') as f:
        elf = ELFFile(f)
        text_section = elf.get_section_by_name('.text')
        if text_section:
            print(f"fount .text section")
            text_data = text_section.data()
            sendPacket(ser, packet_type=0x00, data=text_data)
        else:
            print(f".text section not found in ELF file.")
        
        data_section = elf.get_section_by_name('.data')
        if data_section:
            print(f"fount .data section")
            data_data = data_section.data()
            sendPacket(ser, packet_type=0x01, data=data_data)
        else:
            print(f".data section not found in ELF file.")
            # Send 4 bytes of zeros (one word) to address 0 of Data Memory
            dummy_data = b'\x00\x00\x00\x00'
            sendPacket(ser, packet_type=0x01, data=dummy_data)
    

    print("Upload complete.")

def sendPacket(ser, packet_type, data):
    length = len(data)
    header = struct.pack("<B", packet_type) + length.to_bytes(3, "little") # < for little-endian, B for unsigned char, I for unsigned int (4 bytes)

    print(f"sending header: type={packet_type} with length {length}")
    ser.write(header)

    ser.write(data)
    time.sleep(0.01)  # Small delay to ensure data is sent properly

def listenToUART(ser): # For continuous listening (debugging)
    buffer = bytearray()
    try:
        output = bytearray()
        while True: # Dump content of x10, which is 32 b 
            if ser.in_waiting > 0:
                data = ser.read(ser.in_waiting)
                buffer.extend(data)
                while len(buffer) >= 4:
                    packet = buffer[:4]
                    buffer = buffer[4:]
                    val = struct.unpack("<I", packet)[0] 
                    output.append(val)

            print(f"Debug register value: {hex(val)}: ", end='', flush=True)
            time.sleep(0.1)
    except KeyboardInterrupt:
        print("Stopping UART listener.")

def listenToUART_OneShot(ser): # For listening for a single event
    print("Waiting for FPGA button press...")
    
    # Block until we receive exactly 4 bytes (32 bits)
    data = ser.read(8)  # Read 8 bytes for cycles and x10 value
    
    if len(data) == 8:
        # Unpack as Little Endian (<) Unsigned Int (I)
        result_val, cycles = struct.unpack("<II", data)
        print("=" * 30)
        print(f"Cycles taken:       {cycles}")
        print("-" * 30)
        time_ns = cycles * (1e9 / 100_000_000)  # Assuming a 100 MHz clock, may change depending on our how well we optimize our current processer implementation
        print(f"Execution time:     {time_ns:.2f} ns")
        print("=" * 30)
        print(f"Register x10 Value: {result_val}")
        print("-" * 30)
        print(f"Hexadecimal:        {hex(result_val)}")
        print("=" * 30)
    else:
        print("Timeout or Error: Did not receive 4 full bytes.")

ser = serial.Serial(PORT, BAUDRATE, timeout=1)
uploadFirmware(ser, FILE)
ser.close()
ser = serial.Serial(PORT, BAUDRATE, timeout=50)
listenToUART_OneShot(ser)
ser.close()
print("Closed UART port.")