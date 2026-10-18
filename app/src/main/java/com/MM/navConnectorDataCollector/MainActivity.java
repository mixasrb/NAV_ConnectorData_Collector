package com.MM.navConnectorDataCollector;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.MM.navConnectorDataCollector.BLE.BluetoothLeService;
import com.MM.navConnectorDataCollector.view.DisplayManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MainActivity extends AppCompatActivity {
    BluetoothLeService bluetoothLeService = new BluetoothLeService(this, this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        new DisplayManager(this);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
        } else bluetoothLeService.Initialize();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)

            bluetoothLeService.Initialize();
    }

    public BlockingQueue<ByteBuffer> linResponsesBuffer = new ArrayBlockingQueue<>(1024);

    @Override
    public void onStart() {
        super.onStart();

        bluetoothLeService.OnStart();

        // Start a continuous background consumer thread
        new Thread(() -> {
            String TAG = "LIN";
            String filename = "NAV_Connector_Collected_Data.txt";

            // Set up the file properties once for the MediaStore database
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");

            // Route it to the "MM" folder inside the public Downloads directory
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/MM/");

            Uri externalUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri fileUri = getContentResolver().insert(externalUri, values);

            long responsesCounter[] = new long[0x40];

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Blocks here until a new ESP32 message arrives

                    ByteBuffer buffer = linResponsesBuffer.take();

                    // Parse raw bytes matching ESP32 C++ packed structure
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 11; i++) {
                        switch (i) {
                            case (0):
                                sb.append("Type: ")
                                        .append("0x")
                                        .append(Integer.toHexString(Byte.toUnsignedInt(buffer.get())).toUpperCase())
                                        .append('\n');
                                break;
                            case (1):
                                sb.append("Error: ")
                                        .append("0x")
                                        .append(Integer.toHexString(Byte.toUnsignedInt(buffer.get())).toUpperCase())
                                        .append('\n');
                                break;
                            case (2):
                                byte header = buffer.get();
                                responsesCounter[header]++;
                                sb.append("Header: ")
                                        .append("0x")
                                        .append(Integer.toHexString(Byte.toUnsignedInt(header)).toUpperCase())
                                        .append('\n');
                                break;
                            default:
                                sb.append("Data[").append(i - 3).append("] ")
                                        .append(Integer.toString(Byte.toUnsignedInt(buffer.get())).toUpperCase())
                                        .append('\n');
                        }
                    }

                    String dataStr = sb.toString();

                    long packedTime = Integer.toUnsignedLong(buffer.getInt());
                    long h = (packedTime >> 22) & 0x3FF;
                    long m = (packedTime >> 16) & 0x3F;
                    long s = (packedTime >> 10) & 0x3F;
                    long ms = packedTime & 0x3FF;
                    String timeStr = String.format("%02d:%02d:%02d.%03d", h, m, s, ms);

                    String txtStr = dataStr + "Timestamp: " + timeStr + '\n';

                    // Update layout console
                    runOnUiThread(() -> {
                        DisplayManager.printToConsole(TAG, "Data:\n" + txtStr);

                        StringBuilder sbr = new StringBuilder();
                        for (int i = 0; i < 0x40; i++) {
                            if (responsesCounter[i] > 0)
                                sbr.append("0x")
                                        .append(Integer.toHexString(i).toUpperCase())
                                        .append(" ")
                                        .append(responsesCounter[i])
                                        .append("; ");
                        }

                        DisplayManager.printResponseCount(sbr.toString());
                    });

                    // Append data straight to the public fileUri
                    if (fileUri != null) {
                        try (OutputStream os = getContentResolver().openOutputStream(fileUri, "wa")) {
                            if (os != null) {
                                os.write(txtStr.getBytes());
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing packet to Download/MM/", e);
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing incoming packet", e);
                }
            }
        }).start();
    }
}