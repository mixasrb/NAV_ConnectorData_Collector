package com.MM.navConnectorDataCollector.BLE;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import android.util.Log;

import com.MM.navConnectorDataCollector.MainActivity;
import com.MM.navConnectorDataCollector.view.DisplayManager;

import java.util.UUID;


public class BluetoothLeService {
    private MainActivity mainActivity;
    private final Context context;

    public BluetoothLeService(Context addContext, MainActivity newActivity) {
        context = addContext;
        mainActivity = newActivity;
    }

    public static final String TAG = "BLE";
    private BluetoothManager bluetoothManager = null;
    private BluetoothAdapter bluetoothAdapter = null;

    private void Initialize() {
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
    }

    BluetoothLeScanner bleScanner;

    ScanCallback leScanCallback = new ScanCallback() {
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
            if (deviceName != null && deviceName.equals("ESP32_GATT_Peripheral")) {

                DisplayManager.printToConsole(
                        "BLE_LOG", "Target found! Halting scanner and establishing GATT link...");
                bleScanner.stopScan(leScanCallback); // Stop scanning immediately to stabilize the radio

                // STEP C: Connecting
                // The system knows which device because you invoke it ON the caught 'device' instance
                BluetoothGatt bluetoothGatt = device.connectGatt(context, false, gattCallback);
            }
        }
    };

    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                DisplayManager.printToConsole(
                        TAG, "Connected to GATT server. Starting service discovery...");
                // Crucial step: You must discover services before reading data
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                DisplayManager.printToConsole(
                        TAG, "Disconnected from GATT server.");
                gatt.close(); // Clean up memory immediately
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Target the specific Service and Characteristic UUIDs you need
                UUID serviceUuid = UUID.fromString("4fa4c1a1-1217-4bb9-94c3-154e1f74198a");
                UUID charUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

                BluetoothGattService service = gatt.getService(serviceUuid);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
                    if (characteristic != null) {
                        // Read the characteristic asynchronously
                        gatt.readCharacteristic(characteristic);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Raw data arrives as a byte array
                byte[] rawData = characteristic.getValue();
                DisplayManager.printToConsole(
                        TAG, "Data received, byte length: " + rawData.length);
            }
        }
    };

    public void onCreate() {
        Initialize();

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        if (bleScanner == null) {
            DisplayManager.printToConsole(
                    TAG, "Bluetooth scanner is not initialized");
        }
    }

    public void onStart(){
        bleScanner.startScan(leScanCallback);
    }

}
