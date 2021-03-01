package com.callrecord.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class Storage {
    private static final String TAG = Storage.class.getSimpleName();

    protected static boolean permittedForce = false; // bugged phones has no PackageManager.ACTION_REQUEST_PERMISSIONS activity. allow it all.

    public static final String SCHEME_PACKAGE = "package";

    public static final String COLON = ":";


    // String functions

    public static boolean permitted(Context context, String[] ss) {
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean permitted(Activity a, String[] ss, int code) {
        if (permittedForce)
            return true;
        if (Build.VERSION.SDK_INT < 16)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(a, s) != PackageManager.PERMISSION_GRANTED) {
                try {
                    ActivityCompat.requestPermissions(a, ss, code);
                } catch (ActivityNotFoundException e) {
                    permittedForce = true;
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    public static void showPermissions(Context context) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts(SCHEME_PACKAGE, context.getPackageName(), null);
        intent.setData(uri);
        context.startActivity(intent);
    }
}