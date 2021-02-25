package com.callrecord.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.net.Uri;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.NotificationManagerCompat;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.widgets.NotificationChannelCompat;

public class CallApplication extends com.github.axet.audiolibrary.app.MainApplication {
    public static final String PREFERENCE_FORMAT = "format";
    public static final String PREFERENCE_CALL = "call";
    public static final String PREFERENCE_OPTIMIZATION = "optimization";
    public static final String PREFERENCE_NEXT = "next";
    public static final String PREFERENCE_DETAILS_CONTACT = "_contact";
    public static final String PREFERENCE_DETAILS_CALL = "_call";
    public static final String PREFERENCE_SOURCE = "source";
    public static final String PREFERENCE_VERSION = "version";
    public static final String PREFERENCE_BOOT = "boot";

    public static final String CALL_OUT = "out";
    public static final String CALL_IN = "in";

    public NotificationChannelCompat channelPersistent;

    public static CallApplication from(Context context) {
        return (CallApplication) com.github.axet.audiolibrary.app.MainApplication.from(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        channelPersistent = new NotificationChannelCompat(this, "icon", "Persistent Icon", NotificationManagerCompat.IMPORTANCE_LOW);

        OptimizationPreferenceCompat.setPersistentServiceIcon(this, true);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = shared.edit();

        edit.putInt(PREFERENCE_VERSION, 2);
        edit.putString(CallApplication.PREFERENCE_RATE, "16000");
        edit.putString(CallApplication.PREFERENCE_CHANNELS, "2");
        edit.putString(CallApplication.PREFERENCE_ENCODING, "ogg");
        edit.putString(CallApplication.PREFERENCE_FORMAT, "%I-%i-%p");
        edit.putString(CallApplication.PREFERENCE_STORAGE, "file:///storage/emulated/0/Call%20Recorder");
        int s;
        if (android.os.Build.VERSION.SDK_INT >= 29){
            s = MediaRecorder.AudioSource.VOICE_RECOGNITION;
        } else{
            s = MediaRecorder.AudioSource.VOICE_CALL;
        }
        edit.putString(CallApplication.PREFERENCE_SOURCE, String.valueOf(s));
        edit.commit();

    }

    public static String getContact(Context context, Uri f) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CONTACT;
        return shared.getString(p, null);
    }

    public static void setContact(Context context, Uri f, String id) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CONTACT;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, id);
        editor.commit();
    }

    public static String getCall(Context context, Uri f) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CALL;
        return shared.getString(p, null);
    }

    public static void setCall(Context context, Uri f, String id) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CALL;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, id);
        editor.commit();
    }

}