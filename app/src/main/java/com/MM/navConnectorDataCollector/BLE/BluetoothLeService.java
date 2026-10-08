package com.MM.navConnectorDataCollector.BLE;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import android.util.Log;

import androidx.annotation.RequiresPermission;

import com.MM.navConnectorDataCollector.MainActivity;
import com.MM.navConnectorDataCollector.view.DisplayManager;

import java.util.UUID;


public class BluetoothLeService {
    private MainActivity mainActivity;
    private final Context context;

    private final UUID serviceUuid = UUID.fromString("4fa4c1a1-1217-4bb9-94c3-154e1f74198a");
    private final UUID charUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    // Standard BLE UUID for Client Characteristic Configuration Descriptor (CCCD)
    private final UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final String DEVICE_NAME = "ESP32_GATT_Peripheral";

    public BluetoothLeService(Context addContext, MainActivity newActivity) {
        context = addContext;
        mainActivity = newActivity;
    }

    public static final String TAG = "BLE";
    private BluetoothManager bluetoothManager = null;
    private BluetoothAdapter bluetoothAdapter = null;

    public void Initialize() {
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = (bluetoothManager != null) ? bluetoothManager.getAdapter() : null;

        if (bluetoothAdapter == null) {
            DisplayManager.printToConsole(
                    TAG, "Bluetooth is not supported on this hardware platform.");
        } else if (!bluetoothAdapter.isEnabled()) {
            DisplayManager.printToConsole(
                    TAG, "Bluetooth manager initialized, " +
                            "but Bluetooth radio is currently turned OFF.");
        } else {
            DisplayManager.printToConsole(
                    TAG, "BluetoothAdapter successfully initialized!");
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (bleScanner == null) {
            DisplayManager.printToConsole(
                    TAG, "Bluetooth scanner is not initialized");
        }
    }

    private BluetoothLeScanner bleScanner;

    private BluetoothGatt bluetoothGatt;

    private final ScanCallback leScanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();

            // TODO: Consider calling
            //    ActivityCompat#requestPermissions for android 12+

            String deviceName = device.getName();
            String deviceAddress = device.getAddress(); // MAC Address
            Log.d(TAG, "Found: '" + deviceName + "' [" + deviceAddress + "]");

            // Target your specific peripheral hardware by filtering its name or MAC address
            if (deviceName != null && deviceName.equals(DEVICE_NAME)) {

                DisplayManager.printToConsole(
                        TAG, "Target found! Halting scanner and establishing GATT link...");
                bleScanner.stopScan(leScanCallback); // Stop scanning immediately to stabilize the radio

                // STEP C: Connecting
                // The system knows which device because you invoke it ON the caught 'device' instance

                if (bluetoothGatt != null) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }

                bluetoothGatt = device.connectGatt(context, false, gattCallback);
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                DisplayManager.printToConsole(
                        TAG, "Connected to GATT server. Starting service discovery...");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                DisplayManager.printToConsole(
                        TAG, "Disconnected from GATT server.");
                gatt.close();

                // Clear the active variable reference so the app knows it's dead
                bluetoothGatt = null;
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(serviceUuid);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
                    if (characteristic != null) {

                        // 1. Tell Android to listen locally for notifications
                        gatt.setCharacteristicNotification(characteristic, true);

                        // 2. Tell the hardware peripheral to start sending notifications
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                            // 3. Push the registration configuration down to the hardware
                            boolean success = gatt.writeDescriptor(descriptor);
                            DisplayManager.printToConsole(
                                    TAG, "Subscribed to updates: " + success);
                        } else {
                            DisplayManager.printToConsole(
                                    TAG, "Error: CCCD Descriptor not found!");
                        }
                    }
                }
            }
        }

        // This will now trigger automatically whenever your peripheral pushes updates
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] rawData = characteristic.getValue();
            DisplayManager.printToConsole(TAG, "Stream update byte length: " + rawData.length);

            // Parse the raw bytes matching your C++ packed structure
            if (rawData != null && rawData.length >= 20) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(rawData).order(java.nio.ByteOrder.LITTLE_ENDIAN);

                // 1. Header (1 byte, unsigned)
                int header = Byte.toUnsignedInt(buffer.get());
                DisplayManager.printToConsole(TAG, "Header: 0x" + Integer.toHexString(header).toUpperCase());

                // 2. Data elements (8 bytes) converted to a String
                byte[] dataBytes = new byte[8];
                buffer.get(dataBytes);
                String dataStr = new String(dataBytes, java.nio.charset.StandardCharsets.US_ASCII);
                DisplayManager.printToConsole(TAG, "Data: " + dataStr);

                // 3. Packed Time (4 bytes)
                long packedTime = Integer.toUnsignedLong(buffer.getInt());
                long h  = (packedTime >> 22) & 0x3FF;
                long m  = (packedTime >> 16) & 0x3F;
                long s  = (packedTime >> 10) & 0x3F;
                long ms = packedTime & 0x3FF;

                String timeStr = String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
                DisplayManager.printToConsole(TAG, "Time: " + timeStr);
            }
        }
    };

    public void Start() {
        bleScanner.startScan(leScanCallback);
    }

}
