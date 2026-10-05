package com.MM.navConnectorDataCollector.view;

import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.MM.navConnectorDataCollector.R;

public class DisplayManager {

    private static DisplayManager instance;
    private static AppCompatActivity activity;

    public DisplayManager(AppCompatActivity newActivity) {
        activity = newActivity;

        activity.setContentView(R.layout.activity_main);

        consoleText = activity.findViewById(R.id.consoleText);
        consoleText.setMovementMethod(new ScrollingMovementMethod());

        instance = this;
    }

    private static TextView consoleText;

    public static void printToConsole(final String TAG, final String message) {
        consoleText.post(() -> {
            // TODO add line buffer
            consoleText.append(TAG + ": " + message + "\n");
        });
    }

}
