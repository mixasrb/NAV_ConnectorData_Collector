package com.MM.navConnectorDataCollector;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.MM.navConnectorDataCollector.BLE.BluetoothLeService;
import com.MM.navConnectorDataCollector.view.DisplayManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

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
        } else
            bluetoothLeService.onCreate();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)

            bluetoothLeService.onCreate();
    }

    @Override
    public void onStart() {
        super.onStart();

        bluetoothLeService.onStart();

        String filename = "myfile.txt";
        try (FileOutputStream fos = MainActivity.this.openFileOutput(filename, Context.MODE_PRIVATE)) {
            fos.write("Hello\nmID: 0xff\nData[0] = 0x00".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = MainActivity.this.openFileInput("myfile.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');

                DisplayManager.printToConsole("FileIO", "File Contents: " + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}