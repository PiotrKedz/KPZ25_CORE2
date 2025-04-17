import asyncio
from bleak import BleakScanner, BleakClient

BATTERY_CHAR_UUID = "00002a19-0000-1000-8000-00805f9b34fb"  # Battery Level UUID


async def notification_handler(sender, data):
    # This function is called every time the ESP32 sends new battery level
    battery_level = int(data[0])
    print(f"Received battery level: {battery_level} %")


async def main():
    print("Scanning for ESP-GATT...")
    devices = await BleakScanner.discover()

    for d in devices:
        if d.name and "ESP-GATT" in d.name:
            async with BleakClient(d.address) as client:
                print("Connected to", d.name)

                # Subscribe to notifications
                await client.start_notify(BATTERY_CHAR_UUID, notification_handler)

                print("Listening for battery notifications... (press Ctrl+C to stop)")

                # Keep the connection open
                while True:
                    await asyncio.sleep(1)

    print("ESP-GATT not found.")


asyncio.run(main())
