import bluetooth
import struct
import time
import math
from micropython import const
from machine import ADC, Pin

# IRQ event constants - keeping original format
_IRQ_CENTRAL_CONNECT = const(1)
_IRQ_CENTRAL_DISCONNECT = const(2)
_IRQ_GATTS_WRITE = const(3)

# Use the same UUIDs as your Kotlin app
SERVICE_UUID = bluetooth.UUID("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
CHAR_UUID = bluetooth.UUID("beb5483e-36e1-4688-b7f5-ea07361b26a8")

# Characteristic properties: Read + Notify
CHAR_PROP_READ_NOTIFY = const(0x02 | 0x10)

# === ADC/Thermistor Configuration ===
Vin = 3.3                # Supply voltage
R_fixed = 36300          # Precision resistor value (same for both)
R25 = 10000              # Thermistor resistance at 25°C
Beta = 3450              # Thermistor Beta constant

# === ADC Setup for Two Thermistors ===
pin0 = Pin(3, Pin.IN)
pin1 = Pin(2, Pin.IN)
adc1 = ADC(pin0)
adc2 = ADC(pin1)
adc1.atten(ADC.ATTN_11DB)
adc2.atten(ADC.ATTN_11DB)

def read_temp(adc):
    """Read temperature from thermistor connected to ADC"""
    try:
        adc_value = adc.read()
        voltage = adc_value / 4095 * Vin
        print("ADC Value: {}, Voltage: {:.2f} V".format(adc_value, voltage))
        
        if voltage <= 0 or voltage >= Vin:
            return None
        
        # === Calculate Thermistor Resistance ===
        R_therm = R_fixed * voltage / (Vin - voltage)
        print("Thermistor R: {:.1f} Ω".format(R_therm))
        
        # === Apply Beta Equation ===
        T0 = 298.15  # 25°C in Kelvin
        temp_K = 1 / (1 / T0 + (1 / Beta) * math.log(R_therm / R25))
        temp_C = temp_K - 273.15
        
        return temp_C
    except Exception as e:
        print(f"Error reading temperature: {e}")
        return None

# Initialize BLE
ble = bluetooth.BLE()
ble.active(True)

# Define service and characteristic
service = (
    SERVICE_UUID,
    ((CHAR_UUID, CHAR_PROP_READ_NOTIFY),),
)

# Register services
handles = ble.gatts_register_services((service,))
char_handle = handles[0][0]
print(f"Characteristic handle: {char_handle}")

# Connection state
connected = False
conn_handle = None

def advertising_payload(name="ESP-GATT"):
    """Create simple BLE advertising payload"""
    payload = bytearray()
    # Flags (LE General Discoverable Mode + BR/EDR Not Supported)
    payload += struct.pack("BB", 2, 0x01) + b"\x06"
    # Complete Local Name
    name_bytes = name.encode()
    payload += struct.pack("BB", len(name_bytes) + 1, 0x09) + name_bytes
    return payload

def bt_irq(event, data):
    """Bluetooth interrupt handler"""
    global connected, conn_handle
    
    print(f"BLE IRQ Event: {event}, Data: {data}")  # Debug output
    
    if event == _IRQ_CENTRAL_CONNECT:
        conn_handle = data[0]
        connected = True
        print(f">>> CLIENT CONNECTED with handle: {conn_handle}")
        
    elif event == _IRQ_CENTRAL_DISCONNECT:
        connected = False
        conn_handle = None
        print(">>> CLIENT DISCONNECTED")
        start_advertising()
        
    elif event == _IRQ_GATTS_WRITE:
        conn_handle_evt, attr_handle = data
        print(f">>> WRITE EVENT: conn_handle={conn_handle_evt}, attr_handle={attr_handle}")
        # This indicates the client is interacting with our service

def start_advertising():
    """Start BLE advertising"""
    try:
        ble.gap_advertise(100, adv_data=advertising_payload())
        print(">>> ADVERTISING as ESP-GATT...")
    except Exception as e:
        print(f"Error starting advertising: {e}")

# Set up BLE interrupt handler
ble.irq(bt_irq)

# Small delay to ensure BLE is fully initialized
time.sleep(0.5)

start_advertising()

print("=== BLE Temperature Sensor Started ===")
print(f"Service UUID: {SERVICE_UUID}")
print(f"Characteristic UUID: {CHAR_UUID}")
print("Waiting for connections...")

# Battery simulation parameters
t = 0
initial_battery = 100.0
battery_discharge_rate = 0.001

# Main loop
while True:
    if connected and conn_handle is not None:
        # Read real temperature data from thermistors
        print("\n--- Reading Thermistor 1 ---")
        temperature1 = read_temp(adc1)
        if temperature1 is None:
            print("Thermistor 1: Error reading temperature, using fallback")
            temperature1 = 25.0  # Fallback temperature
        else:
            print("Temperature 1: {:.2f} °C".format(temperature1))
        
        print("\n--- Reading Thermistor 2 ---")
        temperature2 = read_temp(adc2)
        if temperature2 is None:
            print("Thermistor 2: Error reading temperature, using fallback")
            temperature2 = 25.0  # Fallback temperature
        else:
            print("Temperature 2: {:.2f} °C".format(temperature2))
        
        # Battery simulation (you can replace this with real battery reading)
        battery_level = initial_battery - (t * battery_discharge_rate * 100)
        battery_level = max(0.0, min(initial_battery, battery_level))
        
        # Round values
        temperature1 = round(temperature1, 2)
        temperature2 = round(temperature2, 2) 
        battery_level = round(battery_level, 2)
        
        try:
            # Pack as 3 floats in little-endian format
            data = struct.pack("<fff", temperature1, temperature2, battery_level)
            
            # Write to characteristic
            ble.gatts_write(char_handle, data)
            
            # Send notification
            ble.gatts_notify(conn_handle, char_handle)
            
            print(f">>> SENT: Temp1={temperature1:.2f}°C, Temp2={temperature2:.2f}°C, Battery={battery_level:.2f}%")
            
        except Exception as e:
            print(f">>> ERROR sending data: {e}")
            # Reset connection on error
            connected = False
            conn_handle = None
            start_advertising()  # Restart advertising
    else:
        print(">>> Waiting for client connection...")
    
    t += 0.2
    time.sleep(2)  # Send data every 2 seconds