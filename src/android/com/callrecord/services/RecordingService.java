package com.callrecord.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.callrecord.app.Storage;
import com.plain.R;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * RecordingActivity more likly to be removed from memory when paused then service. Notification button
 * does not handle getActvity without unlocking screen. The only option is to have Service.
 * <p/>
 * So, lets have it.
 * <p/>
 * Maybe later this class will be converted for fully feature recording service with recording thread.
 */
public class RecordingService extends Service {
    public static final String TAG = RecordingService.class.getSimpleName();
    private static final String AUDIO_RECORDER_FOLDER = "Call Record";
    MediaRecorder recorder;

    public static final int DEFAULT_RATE = 16000;

    private static final String LOG_TAG = RecordingService.class.getSimpleName();

    public static final String CALL_OUT = "out";
    public static final String CALL_IN = "in";

    public static final int RESULT_CALL = 1;

    public static final String[] CONTACTS = new String[]{
            Manifest.permission.READ_CONTACTS,
    };

    public static final String[] MUST = new String[]{
            Manifest.permission.RECORD_AUDIO,
    };

    public static final String[] PERMISSIONS = RecordingService.concat(MUST, new String[]{
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


    Thread thread;
    PhoneStateReceiver state;
    PhoneStateChangeListener pscl;
    String phone = "";
    String contact = "";
    String contactId = "";
    String call;

    int sampleRate; // variable from settings. how may samples per second.

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static void Error(Context context, Throwable e) {
        Log.d(TAG, "Error", e);
//        Toast.Error(context, "CallRecorder: " + ErrorDialog.toMessage(e));
    }


    class PhoneStateReceiver extends BroadcastReceiver {
        IntentFilter filters;

        public PhoneStateReceiver() {
            filters = new IntentFilter();
            filters.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            filters.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        }

        public void register(Context context) {
            context.registerReceiver(this, filters);
        }

        public void unregister(Context context) {
            context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            String a = intent.getAction();
            if (a.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
                setPhone(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER), call);
            if (a.equals(Intent.ACTION_NEW_OUTGOING_CALL))
                setPhone(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER), RecordingService.CALL_OUT);
        }
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;
        public boolean startedByCall;
        public TelephonyManager tm;

        public PhoneStateChangeListener() {
            tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        }

        public void register() {
            tm.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
        }

        public void unregister() {
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
        }

        @Override
        public void onCallStateChanged(final int s, final String incomingNumber) {
            Log.d(TAG, "onCallStateChanged");
            try {
                switch (s) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        setPhone(incomingNumber, RecordingService.CALL_IN);
                        wasRinging = true;
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        setPhone(incomingNumber, call);
                        if (thread == null) { // handling restart while current call
                            begin();
                            startedByCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (startedByCall) {
                            if (tm.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) // current state maybe differed from queued (s) one
                                finish();
                            else
                                return; // fast clicking. new call already stared. keep recording. do not reset startedByCall
                        }
                        wasRinging = false;
                        startedByCall = false;
                        phone = "";
                        contactId = "";
                        contact = "";
                        call = "";
                        break;
                }
            } catch (RuntimeException e) {
                Error(RecordingService.this, e);
            }
        }
    }

    public RecordingService() {
    }

    public void setPhone(String s, String c) {
        if (s == null || s.isEmpty())
            return;

        phone = PhoneNumberUtils.formatNumber(s);

        contact = "";
        contactId = "";
        if (Storage.permitted(this, RecordingService.CONTACTS)) {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(s));
            try {
                ContentResolver contentResolver = getContentResolver();
                Cursor contactLookup = contentResolver.query(uri, null, null, null, null);
                if (contactLookup != null) {
                    try {
                        if (contactLookup.moveToNext()) {
                            contact = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                            contactId = contactLookup.getString(contactLookup.getColumnIndex(BaseColumns._ID));
                        }
                    } finally {
                        contactLookup.close();
                    }
                }
            } catch (RuntimeException e) {
                Error(RecordingService.this, e);
            }
        }

        call = c;
    }


    @Override
    public void onCreate() {
        super.onCreate();

        pscl = new PhoneStateChangeListener();
        pscl.register();

        state = new PhoneStateReceiver();
        state.register(this);

        sampleRate =  RecordingService.DEFAULT_RATE; //Sound.getSampleRate(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());

//        startForeground(1, new Notification());

    }

    private void startMyOwnForeground(){

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){

            String NOTIFICATION_CHANNEL_ID = "com.callrecorder";
            String channelName = "My Background Service";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setContentTitle("Call Recording is Active")
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();
            startForeground(2, notification);
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();


//        stopRecording();


        if (state != null) {
            state.unregister(this);
            state = null;
        }

        if (pscl != null) {
            pscl.unregister();
            pscl = null;
        }
    }


    void startRecording() {

        int s;
        if (android.os.Build.VERSION.SDK_INT >= 29){
            s = MediaRecorder.AudioSource.VOICE_RECOGNITION;
        } else{
            s = MediaRecorder.AudioSource.VOICE_CALL;
        }

        recorder = new MediaRecorder();
        recorder.setAudioSource(s);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(getFilename());
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
        recorder.start();
    }

    private String getFilename()
    {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }
        return (file.getAbsolutePath() + "/" + Storage.getFormatted(phone, contact, call) + ".3gp");
    }

    void stopRecording() {
        recorder.stop();
        recorder.release();
        recorder = null;
    }
    void begin() {
        startRecording();
    }

    public static void start(Context context) {
        RecordingService.startService(context, new Intent(context, RecordingService.class));
    }

    public static ComponentName startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26 && context.getApplicationInfo().targetSdkVersion >= 26) {
            Class k = context.getClass();
            try {
                Method m = k.getMethod("startForegroundService", Intent.class);
                return (ComponentName) m.invoke(context, intent);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else {
            return context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand " + action);

        }
        return super.onStartCommand(intent, flags, startId);
    }




    void finish() {
        stopRecording();
    }
}