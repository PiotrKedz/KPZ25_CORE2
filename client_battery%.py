import asyncio
from bleak import BleakScanner, BleakClient

BATTERY_CHAR_UUID = "00002a19-0000-1000-8000-00805f9b34fb"


async def main():
    print("Scanning for ESP-GATT...")
    devices = await BleakScanner.discover()

    for d in devices:
        if d.name and "ESP-GATT" in d.name:
            async with BleakClient(d.address) as client:
                print("Connected to", d.name)
                value = await client.read_gatt_char(BATTERY_CHAR_UUID)
                print("Battery Level:", int(value[0]), "%")
            return  # exit after connecting once

    print("ESP-GATT not found.")

asyncio.run(main())
