package com.callrecord.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

public class OnExternalReceiver extends BroadcastReceiver {

    public static final String TAG = OnExternalReceiver.class.getSimpleName();

    public static boolean isExternal(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo ai = pi.applicationInfo;
            return (ai.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive " + intent);
        if (!isExternal(context))
            return;
        onBootReceived(context);
    }

    public void onBootReceived(Context context) {
        RecordingService.startIfEnabled(context);
    }
}
