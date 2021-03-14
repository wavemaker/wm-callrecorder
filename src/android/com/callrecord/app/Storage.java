package com.callrecord.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.callrecord.services.RecordingService;

public class Storage {
    private static final String TAG = Storage.class.getSimpleName();

    protected static boolean permittedForce = false; // bugged phones has no PackageManager.ACTION_REQUEST_PERMISSIONS activity. allow it all.
    public static final String SEPERATOR = "_";


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




    public static String getFormatted( String phone, String contact, String call) {
        String format = "";
        switch (call) {
            case RecordingService.CALL_IN:
                format += "Incoming" + SEPERATOR;
                break;
            case RecordingService.CALL_OUT:
                format += "Outgoing" + SEPERATOR;
                break;
        }
        if (phone != null && !phone.isEmpty())
            format += phone + SEPERATOR;
        if (contact != null && !contact.isEmpty())
            format += contact + SEPERATOR;
        format += System.currentTimeMillis();
            return format;
    }
}