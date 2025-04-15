import bluetooth
import struct
from micropython import const

# BLE event codes
_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)

# Standard Battery Service + Characteristic UUIDs
SERVICE_UUID = bluetooth.UUID(0x180F)        # Battery Service
CHAR_UUID = bluetooth.UUID(0x2A19)           # Battery Level

CHAR_PROP_READ = const(0x02)

ble = bluetooth.BLE()
ble.active(True)

# Register GATT service
service = (
    SERVICE_UUID,
    (
        (CHAR_UUID, CHAR_PROP_READ),
    ),
)
handles = ble.gatts_register_services((service,))
char_handle = handles[0][0]

# Set mock battery level (87%)
ble.gatts_write(char_handle, struct.pack("B", 87))

# Advertising payload builder
def advertising_payload(name="ESP-GATT"):
    payload = bytearray()
    payload += struct.pack("BB", 2, 0x01) + b"\x06"  # Flags
    name_bytes = name.encode()
    payload += struct.pack("BB", len(name_bytes) + 1, 0x09) + name_bytes
    return payload

# BLE event handler
def bt_irq(event, data):
    if event == _IRQ_CENTRAL_CONNECT:
        print("Client connected")
    elif event == _IRQ_CENTRAL_DISCONNECT:
        print("Client disconnected")
        start_advertising()

# Start advertising
def start_advertising():
    payload = advertising_payload()
    ble.gap_advertise(100_000, adv_data=payload)
    print("Advertising...")

# Set up IRQ and start BLE
ble.irq(bt_irq)
start_advertising()
