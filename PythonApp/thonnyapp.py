import bluetooth
import struct
import time
from micropython import const

_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)

SERVICE_UUID = bluetooth.UUID(0x180F)  # Custom Service (reusing Battery UUID for now)
CHAR_UUID = bluetooth.UUID(0x2A19)     # Custom Characteristic (reusing Battery Level UUID)

CHAR_PROP_READ_NOTIFY = const(0x02 | 0x10)

ble = bluetooth.BLE()
ble.active(True)

# Register service and characteristic
service = (
    SERVICE_UUID,
    (
        (CHAR_UUID, CHAR_PROP_READ_NOTIFY),
    ),
)
handles = ble.gatts_register_services((service,))
char_handle = handles[0][0]

connected = False
conn_handle = None

# Simple advertising payload
def advertising_payload(name="ESP-GATT"):
    payload = bytearray()
    payload += struct.pack("BB", 2, 0x01) + b"\x06"
    name_bytes = name.encode()
    payload += struct.pack("BB", len(name_bytes) + 1, 0x09) + name_bytes
    return payload

# BLE event handler
def bt_irq(event, data):
    global connected, conn_handle
    if event == _IRQ_CENTRAL_CONNECT:
        conn_handle, _, _ = data
        connected = True
        print("Client connected")
    elif event == _IRQ_CENTRAL_DISCONNECT:
        connected = False
        conn_handle = None
        print("Client disconnected")
        start_advertising()

def start_advertising():
    ble.gap_advertise(100, adv_data=advertising_payload())
    print("Advertising...")

ble.irq(bt_irq)
start_advertising()

# Hardcoded sensor values
temperature1 = 30.0
temperature2 = 35.0
battery_level = 50.0

# Main loop
while True:
    if connected and conn_handle is not None:
        # Pack all three floats into a single 12-byte array
        data = struct.pack("<fff", temperature1, temperature2, battery_level)
        ble.gatts_write(char_handle, data)
        ble.gatts_notify(conn_handle, char_handle)
        print("Sent data:", data)
    time.sleep(1)
