package com.callrecord.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.callrecord.R;
import com.callrecord.app.Storage;
import com.callrecord.services.RecordingService;

import java.util.Arrays;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int RESULT_CALL = 1;

    public static final String[] CONTACTS = new String[]{
            Manifest.permission.READ_CONTACTS,
    };

    public static final String[] MUST = new String[]{
            Manifest.permission.RECORD_AUDIO,
    };

    public static final String[] PERMISSIONS = MainActivity.concat(MUST, new String[]{
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAPTURE_AUDIO_OUTPUT,
            Manifest.permission.READ_CONTACTS, // get contact name by phone number
            Manifest.permission.READ_PHONE_STATE, // read outgoing going calls information
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    });



    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);


        RecordingService.start(this);


        View rec = findViewById(R.id.record);
        rec.setOnClickListener(this);
    }



    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.record:
                if (!Storage.permitted(MainActivity.this, PERMISSIONS, RESULT_CALL)) {
                }
                break;

        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_CALL:
                if (Storage.permitted(this, MUST)) {

                    RecordingService.start(this);
                } else {
                    Toast.makeText(this, "Not permitteed", Toast.LENGTH_SHORT).show();
                    if (!Storage.permitted(this, MUST)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Permissions");
                        builder.setMessage("Call permissions must be enabled manually");
                        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Storage.showPermissions(MainActivity.this);
                            }
                        });
                        builder.show();
                    }
                }
        }
    }

}