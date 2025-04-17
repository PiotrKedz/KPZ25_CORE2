import bluetooth
import struct
import time
from micropython import const

_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)

SERVICE_UUID = bluetooth.UUID(0x180F)
CHAR_UUID = bluetooth.UUID(0x2A19)

CHAR_PROP_READ_NOTIFY = const(0x02 | 0x10)

ble = bluetooth.BLE()
ble.active(True)

# Add CCCD (Client Characteristic Configuration Descriptor) manually
service = (
    SERVICE_UUID,
    (
        (CHAR_UUID, CHAR_PROP_READ_NOTIFY, (
            (bluetooth.UUID(0x2902), bluetooth.FLAG_READ | bluetooth.FLAG_WRITE),
        )),
    ),
)
handles = ble.gatts_register_services((service,))
char_handle = handles[0][0]

ble.gatts_write(char_handle, struct.pack("B", 87))

connected = False
conn_handle = None

def advertising_payload(name="ESP-GATT"):
    payload = bytearray()
    payload += struct.pack("BB", 2, 0x01) + b"\x06"
    name_bytes = name.encode()
    payload += struct.pack("BB", len(name_bytes) + 1, 0x09) + name_bytes
    return payload

def bt_irq(event, data):
    global connected, conn_handle
    if event == _IRQ_CENTRAL_CONNECT:
        conn_handle, _, _ = data
        connected = True
        print("Client connected, conn_handle:", conn_handle)
    elif event == _IRQ_CENTRAL_DISCONNECT:
        connected = False
        conn_handle = None
        print("Client disconnected")
        start_advertising()

def start_advertising():
    payload = advertising_payload()
    ble.gap_advertise(100_000, adv_data=payload)
    print("Advertising...")

ble.irq(bt_irq)
start_advertising()

battery_level = 87

while True:
    if connected and conn_handle is not None:
        battery_level = (battery_level + 1) % 101
        ble.gatts_write(char_handle, struct.pack("B", battery_level))
        ble.gatts_notify(conn_handle, char_handle)
        print("Sent battery level:", battery_level)
    time.sleep(2)
