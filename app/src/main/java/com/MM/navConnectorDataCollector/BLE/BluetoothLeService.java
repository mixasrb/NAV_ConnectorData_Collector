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

import androidx.annotation.RequiresPermission;

import com.MM.navConnectorDataCollector.MainActivity;
import com.MM.navConnectorDataCollector.view.DisplayManager;

import java.util.UUID;


public class BluetoothLeService {
    public static final String TAG = "BLE";
    private MainActivity mainActivity;
    private final Context context;

    private static final UUID serviceUuid = UUID.fromString("4fa4c1a1-1217-4bb9-94c3-154e1f74198a");
    private static final UUID charUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    // Standard BLE UUID for Client Characteristic Configuration Descriptor (CCCD)
    private static  final UUID cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String TARGET_DEVICE_NAME = "ESP32_GATT_Peripheral";

    public BluetoothLeService(Context addContext, MainActivity activity) {
        context = addContext;
        mainActivity = activity;
    }

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

    private BluetoothLeScanner bleScanner = null;
    private BluetoothGatt bluetoothGatt = null;

    private final ScanCallback bleScanCallback = new ScanCallback() {
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN})
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();

            // TODO: Consider calling
            //    ActivityCompat#requestPermissions for android 12+

            String deviceName = device.getName();
            String deviceAddress = device.getAddress(); // MAC Address

            DisplayManager.printToConsole(TAG, "Found: '" + deviceName + "' [" + deviceAddress + "]");

            // Target your specific peripheral hardware by filtering its name or MAC address
            if (deviceName != null && deviceName.equals(TARGET_DEVICE_NAME)) {

                bleScanner.stopScan(bleScanCallback); // Stop scanning immediately to stabilize the radio

                if (bluetoothGatt != null) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }

                bluetoothGatt = device.connectGatt(context, false, gattCallback);

                DisplayManager.printToConsole(
                        TAG, "Target found! Halting scanner and establishing GATT link...");
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                DisplayManager.printToConsole(
                        TAG, "Disconnected from GATT server.");
                gatt.close();

                // Clear the active variable reference so the app knows it's dead
                bluetoothGatt = null;

                // TODO: Somehow looks wrong, FIX IT!
                bleScanner.startScan(bleScanCallback);
                return;
            }

            gatt.discoverServices();

            DisplayManager.printToConsole(
                    TAG, "Connected to GATT server. Starting service discovery...");
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS)
                return;

            BluetoothGattService service = gatt.getService(serviceUuid);
            if (service == null)
                return;

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
            if (characteristic == null)
                return;

            // Tell Android to listen locally for notifications
            gatt.setCharacteristicNotification(characteristic, true);

            // Tell the hardware peripheral to start sending notifications
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(cccdUuid);
            if (descriptor == null) {
                DisplayManager.printToConsole(
                        TAG, "Error: CCCD Descriptor not found!");
                return;
            }

            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            // Push the registration configuration down to the hardware
            boolean success = gatt.writeDescriptor(descriptor);
            DisplayManager.printToConsole(
                    TAG, "Subscribed to updates: " + success);
        }

        // This will trigger automatically whenever your peripheral pushes updates
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] rawData = characteristic.getValue();
            DisplayManager.printToConsole(TAG, "Stream update byte length: " + rawData.length);


            if (rawData == null && rawData.length < 20) {
                DisplayManager.printToConsole(TAG, "Update dropped, byte length is < 20");
                return;
            }

            // Parse the raw bytes matching ESP32 C++ packed structure

            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(rawData).order(java.nio.ByteOrder.LITTLE_ENDIAN);

            // Header (1 byte, unsigned)
            int header = Byte.toUnsignedInt(buffer.get());
            DisplayManager.printToConsole(TAG, "Header: 0x" + Integer.toHexString(header).toUpperCase());

            // Data elements (8 bytes) converted to a String
            byte[] dataBytes = new byte[8];
            buffer.get(dataBytes);
            String dataStr = new String(dataBytes, java.nio.charset.StandardCharsets.US_ASCII);
            DisplayManager.printToConsole(TAG, "Data: " + dataStr);

            // Packed Time (4 bytes)
            long packedTime = Integer.toUnsignedLong(buffer.getInt());
            long h = (packedTime >> 22) & 0x3FF;
            long m = (packedTime >> 16) & 0x3F;
            long s = (packedTime >> 10) & 0x3F;
            long ms = packedTime & 0x3FF;

            String timeStr = String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
            DisplayManager.printToConsole(TAG, "Time: " + timeStr);

        }
    };

    public void OnStart() {
        bleScanner.startScan(bleScanCallback);
    }
}
