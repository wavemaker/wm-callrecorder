package com.example.plugin;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import com.callrecord.app.Storage;
import com.callrecord.services.RecordingService;


public class Hello extends CordovaPlugin {
    public static final int RESULT_CALL = 1;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        if (action.equals("greet")) {
            cordova.requestPermissions(this, RESULT_CALL,RecordingService.PERMISSIONS );

//            this.cordova.getThreadPool().execute(new Runnable() {
//            public void run() {
//                cordova.requestPermissions(this, 200,RecordingService.PERMISSIONS );
//
//
////                if (!Storage.permitted(cordova.getActivity(), RecordingService.PERMISSIONS, RecordingService.RESULT_CALL)) {
////                }
//                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
//            }
//        });


        }

        return false;
        // if (action.equals("greet")) {

        //     String name = data.getString(0);
        //     String message = "Hello, " + name;
        //     callbackContext.success(message);

        //     return true;

        // } else {
            
        //     return false;

        // }
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
//        for(int r:grantResults)
//        {
//            if(r == PackageManager.PERMISSION_DENIED)
//            {
//                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
//                return;
//            }
//        }
        switch(requestCode)
        {
            case RESULT_CALL:
                if (Storage.permitted(this.cordova.getContext(), RecordingService.MUST)) {

                    RecordingService.start(this.cordova.getContext());
                } else {
                    Toast.makeText(this.cordova.getContext(), "Not permitteed", Toast.LENGTH_SHORT).show();
                    if (!Storage.permitted(this.cordova.getContext(), RecordingService.MUST)) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this.cordova.getContext());
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
//                                Storage.showPermissions();
                            }
                        });
                        builder.show();
                    }
                }
                break;
        }
    }

}