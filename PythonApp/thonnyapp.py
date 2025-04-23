import bluetooth
import struct
import time
import math
from micropython import const

_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)

SERVICE_UUID = bluetooth.UUID(0x180F)  # Custom Service
CHAR_UUID = bluetooth.UUID(0x2A19)  # Custom Characteristic

CHAR_PROP_READ_NOTIFY = const(0x02 | 0x10)

ble = bluetooth.BLE()
ble.active(True)

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

# Simulated data loop
t = 0
while True:
    # Simulate data with sin wave + offset
    temperature1 = 36.5 + math.sin(t) * 1.0  # Range: 35.5 - 37.5
    temperature2 = 37.0 + math.cos(t / 2) * 1.0  # Range: 36.0 - 38.0
    battery_level = 50.0 + math.sin(t / 4) * 10  # Range: 40.0 - 60.0

    temperature1 = round(temperature1, 2)
    temperature2 = round(temperature2, 2)
    battery_level = round(battery_level, 2)

    if connected and conn_handle is not None:
        data = struct.pack("<fff", temperature1, temperature2, battery_level)
        ble.gatts_write(char_handle, data)
        ble.gatts_notify(conn_handle, char_handle)
        print(f"Sent data: temp1={temperature1:.2f}, temp2={temperature2:.2f}, battery={battery_level:.2f}")

    t += 0.2
    time.sleep(1)

