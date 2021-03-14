package com.example.plugin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import com.callrecord.app.Storage;
import com.callrecord.services.RecordingService;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;


public class Callrecord extends CordovaPlugin {
    public static final int RESULT_CALL = 1;
    CallbackContext mCallbackContext;

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        Context context = cordova.getActivity().getApplicationContext();
        String packageName = context.getPackageName();
        mCallbackContext = callbackContext;
        if (action.equals("startRecordingService")) {
            cordova.requestPermissions(this, RESULT_CALL,RecordingService.PERMISSIONS );
            return true;
        } else if(action.equals("openAccessibility")){
            openAccessibility(context, packageName, callbackContext);
            return true;
        } else if(action.equals("openAppSetting")){
            openAppSetting();
            return true;
        } else if(action.equals("excludePowerSaver")){
            this.requestOptimizations(context, packageName, callbackContext);
            return true;
        } else if (action.equals("IsIgnoringBatteryOptimizations")) {
            this.IsIgnoringBatteryOptimizations(context, packageName, callbackContext);
            return true;
        }

        return false;
    }

    public boolean IsIgnoringBatteryOptimizations(Context context, String packageName, CallbackContext callbackContext) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String message = "";
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm.isIgnoringBatteryOptimizations(packageName)) {
                    message ="true";
                }
                else
                {
                    message ="false";
                }
                callbackContext.success(message);
                return true;
            }
            else
            {
                callbackContext.error("BATTERY_OPTIMIZATIONS Not available.");
                return false;
            }
        } catch (Exception e) {
            callbackContext.error("IsIgnoringBatteryOptimizations: failed N/A");
            return false;
        }
    }

    public boolean requestOptimizations(Context context, String packageName, CallbackContext callbackContext) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("package:" + packageName));
                context.startActivity(intent);
                callbackContext.success();
                return true;
            }
            else
            {
                callbackContext.error("BATTERY_OPTIMIZATIONS Not available.");
                return false;
            }
        } catch (Exception e) {
            callbackContext.error("N/A");
            return false;
        }
    }


    public  void openAccessibility(Context context, String packageName, CallbackContext callbackContext) {
        Intent appIntent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        Uri uri = Uri.fromParts("package", cordova.getActivity().getPackageName(), null);
        appIntent.setData(uri);
        context.startActivity(appIntent);
        callbackContext.success();
    }

  public  void openAppSetting() {
        Intent appIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", cordova.getActivity().getPackageName(), null);
        appIntent.setData(uri);
        cordova.getActivity().startActivity(appIntent);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException    {
        switch(requestCode)
        {
            case RESULT_CALL:
                if (Storage.permitted(this.cordova.getContext(), RecordingService.MUST)) {
                    RecordingService.start(this.cordova.getContext());
                    mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
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
                                openAppSetting();
                            }
                        });
                        builder.show();
                        mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
                    }
                }
                break;
        }
    }

}