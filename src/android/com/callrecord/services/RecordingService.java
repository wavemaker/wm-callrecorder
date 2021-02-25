package com.callrecord.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;

import com.github.axet.androidlibrary.app.ProximityShader;
import com.github.axet.androidlibrary.preferences.OptimizationPreferenceCompat;
import com.github.axet.androidlibrary.services.PersistentService;
import com.github.axet.androidlibrary.widgets.ErrorDialog;
import com.github.axet.androidlibrary.widgets.RemoteNotificationCompat;
import com.github.axet.androidlibrary.widgets.Toast;
import com.github.axet.audiolibrary.app.RawSamples;
import com.github.axet.audiolibrary.app.Sound;
import com.github.axet.audiolibrary.encoders.FileEncoder;
import com.github.axet.audiolibrary.encoders.OnFlyEncoding;
import com.callrecord.BuildConfig;
import com.callrecord.R;
import com.callrecord.activities.MainActivity;
import com.callrecord.app.CallApplication;
import com.callrecord.app.Storage;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RecordingActivity more likly to be removed from memory when paused then service. Notification button
 * does not handle getActvity without unlocking screen. The only option is to have Service.
 * <p/>
 * So, lets have it.
 * <p/>
 * Maybe later this class will be converted for fully feature recording service with recording thread.
 */
public class RecordingService extends PersistentService {
    public static final String TAG = RecordingService.class.getSimpleName();

    public static final int SEC1 = 1000;

    public static final int DEFAULT_RATE = 16000;
    public static final int DEFAULT_CHANNEL = 2;

    public static final int NOTIFICATION_PERSISTENT_ICON = 1;
    public static final int RETRY_DELAY = 60 * RecordingService.SEC1; // 1 min

    public static String SHOW_ACTIVITY = RecordingService.class.getCanonicalName() + ".SHOW_ACTIVITY";
    public static String PAUSE_BUTTON = RecordingService.class.getCanonicalName() + ".PAUSE_BUTTON";
    public static String STOP_BUTTON = RecordingService.class.getCanonicalName() + ".STOP_BUTTON";

    Handler handle = new Handler();

    AtomicBoolean interrupt = new AtomicBoolean();
    Thread thread;
    Storage storage;
    PhoneStateReceiver state;
    PhoneStateChangeListener pscl;

    long now;
    Uri targetUri; // output target file 2016-01-01 01.01.01.wav
    String phone = "";
    String contact = "";
    String contactId = "";
    String call;

    int sampleRate; // variable from settings. how may samples per second.
    long samplesTime; // how many samples passed for current recording
    FileEncoder encoder;
    Runnable encoding; // current encoding
    HashMap<File, CallInfo> mapTarget = new HashMap<>();
    int source = -1; // audiorecorder source
    Runnable encodingNext = new Runnable() {
        @Override
        public void run() {
            encodingNext();
        }
    };

    public static void Post(Context context, Throwable e) {
        Log.e(TAG, "Post", e);
        Toast.Post(context, "CallRecorder: " + ErrorDialog.toMessage(e));
    }

    public static void Error(Context context, Throwable e) {
        Log.d(TAG, "Error", e);
        Toast.Error(context, "CallRecorder: " + ErrorDialog.toMessage(e));
    }

    public static void setEnabled(Context context, boolean b) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edit = shared.edit();
        edit.putBoolean(CallApplication.PREFERENCE_CALL, b);
        edit.commit();
        if (b) {
            RecordingService.start(context);
            Toast.makeText(context, R.string.recording_enabled, Toast.LENGTH_SHORT).show();
        } else {
            RecordingService.stop(context);
            Toast.makeText(context, R.string.recording_disabled, Toast.LENGTH_SHORT).show();
        }
    }

    public static void start(Context context) {
        start(context, new Intent(context, RecordingService.class));
    }

    public static boolean isEnabled(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = shared.getBoolean(CallApplication.PREFERENCE_CALL, false);
        if (!Storage.permitted(context, MainActivity.MUST))
            b = false;
        return b;
    }

    public static void startIfEnabled(Context context) {
        if (isEnabled(context))
            start(context);
    }

    public static void stop(Context context) {
        stop(context, new Intent(context, RecordingService.class));
    }

    public static void pauseButton(Context context) {
        Intent intent = new Intent(PAUSE_BUTTON);
        context.sendBroadcast(intent);
    }

    public static void stopButton(Context context) {
        Intent intent = new Intent(STOP_BUTTON);
        context.sendBroadcast(intent);
    }

    public static int[] concat(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public interface Success {
        void run(Uri u);
    }

    public static class CallInfo {
        public Uri targetUri;
        public String phone;
        public String contact;
        public String contactId;
        public String call;
        public long now;

        public CallInfo() {
        }

        public CallInfo(Uri t, String p, String c, String cid, String call, long now) {
            this.targetUri = t;
            this.phone = p;
            this.contact = c;
            this.contactId = cid;
            this.call = call;
            this.now = now;
        }
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
                setPhone(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER), CallApplication.CALL_OUT);
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
                        setPhone(incomingNumber, CallApplication.CALL_IN);
                        wasRinging = true;
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        setPhone(incomingNumber, call);
                        if (thread == null) { // handling restart while current call
                            begin(wasRinging);
                            startedByCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (startedByCall) {
                            if (tm.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) // current state maybe differed from queued (s) one
                                finish();
                            else
                                return; // fast clicking. new call already stared. keep recording. do not reset startedByCall
                        } else {
                            if (storage.recordingPending()) // handling restart after call finished
                                finish();
                            else if (storage.recordingNextPending()) // only call encodeNext if we have next encoding
                                encodingNext();
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
        if (Storage.permitted(this, MainActivity.CONTACTS)) {
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

        storage = new Storage(this);

        pscl = new PhoneStateChangeListener();
        pscl.register();

        state = new PhoneStateReceiver();
        state.register(this);

        sampleRate =  RecordingService.DEFAULT_RATE; //Sound.getSampleRate(this);
        try {
            encodingNext();
        } catch (RuntimeException e) {
            Error(RecordingService.this, e);
        }
    }

    @Override
    public void onCreateOptimization() {
        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, NOTIFICATION_PERSISTENT_ICON, CallApplication.PREFERENCE_OPTIMIZATION, CallApplication.PREFERENCE_NEXT) {
            @Override
            public Notification build(Intent intent) {
                    return buildPersistent(icon.notification);
            }
        };
        optimization.create();
    }

    @Override
    public void onStartCommand(Intent intent) {
        String a = intent.getAction();
        if (a == null) {
            ; // nothing
        } else if (a.equals(PAUSE_BUTTON)) {
            pauseButton(this);
        } else if (a.equals(STOP_BUTTON)) {
            stopButton(this);
        } else if (a.equals(SHOW_ACTIVITY)) {
            ProximityShader.closeSystemDialogs(this);
            MainActivity.startActivity(this);
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

        handle.removeCallbacks(encodingNext);

        stopRecording();

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (state != null) {
            state.unregister(this);
            state = null;
        }

        if (pscl != null) {
            pscl.unregister();
            pscl = null;
        }
    }

    public String getSourceText() {
        switch (source) {
            case MediaRecorder.AudioSource.VOICE_UPLINK:
                return "(VOICE_UPLINK)";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK:
                return "(VOICE_DOWNLINK)";
            case MediaRecorder.AudioSource.VOICE_CALL:
                return getString(R.string.source_line);
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return "(VoIP)";
            case MediaRecorder.AudioSource.MIC:
                return getString(R.string.source_mic);
            case MediaRecorder.AudioSource.DEFAULT:
                return getString(R.string.source_default);
            case MediaRecorder.AudioSource.UNPROCESSED:
                return "(RAW)";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                return "(VOICE_RECOGNITION)";
            case MediaRecorder.AudioSource.CAMCORDER:
                return "(Camcorder)";
            default:
                return "" + source;
        }
    }

    @SuppressLint("RestrictedApi")
    public Notification buildPersistent(Notification when) {
        PendingIntent main = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteNotificationCompat.Builder builder = new RemoteNotificationCompat.Low(this, R.layout.notifictaion);

        builder.setViewVisibility(R.id.notification_pause, View.GONE);
        builder.setViewVisibility(R.id.notification_record, View.GONE);

        builder.setTheme(CallApplication.getTheme(this, R.style.RecThemeLight, R.style.RecThemeDark))
                .setChannel(CallApplication.from(this).channelPersistent)
                .setMainIntent(main)
                .setTitle(getString(R.string.app_name))
                .setText(getString(R.string.recording_enabled))
                .setIcon(R.drawable.ic_call_black_24dp)
                .setImageViewTint(R.id.icon_circle, builder.getThemeColor(R.attr.colorButtonNormal))
                .setWhen(when)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_notification_service);

        return builder.build();
    }

    public void updateIcon(boolean show) {
        optimization.icon.updateIcon(show ? new Intent() : null);
    }

    void startRecording() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        int[] ss = new int[]{
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // mic source VOIP
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.MIC, // mic
                MediaRecorder.AudioSource.DEFAULT, // mic
                MediaRecorder.AudioSource.UNPROCESSED,
        };
        int i;
        int s;
        if (android.os.Build.VERSION.SDK_INT >= 29){
            s = MediaRecorder.AudioSource.VOICE_RECOGNITION;
        } else{
            s = MediaRecorder.AudioSource.VOICE_CALL;
        }
        if (s == -1) {
            i = 0;
        } else {
            i = Sound.indexOf(ss, s);
            if (i == -1) { // missing source, add as first
                ss = concat(new int[]{s}, ss);
                i = 0;
            }
        }



        String ext = shared.getString(CallApplication.PREFERENCE_ENCODING, "");
        startAudioRecorder(ss, i);

        updateIcon(true);
    }

    void startAudioRecorder(int[] ss, int i) {
        final CallInfo info = new CallInfo(targetUri, phone, contact, contactId, call, now);

        final OnFlyEncoding fly = new OnFlyEncoding(storage, info.targetUri, getInfo());

        final AudioRecord recorder = Sound.createAudioRecorder(this, sampleRate, ss, i);
        source = recorder.getAudioSource();

        final Thread old = thread;
        final AtomicBoolean oldb = interrupt;

        interrupt = new AtomicBoolean(false);

        thread = new Thread("RecordingThread") {
            @Override
            public void run() {
                if (old != null) {
                    oldb.set(true);
                    old.interrupt();
                    try {
                        old.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wlcpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, BuildConfig.APPLICATION_ID + ":cpulock");
                wlcpu.acquire();

                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

                Runnable done = new Runnable() {
                    @Override
                    public void run() {
                        stopRecording();
                        updateIcon(false);
                    }
                };

                Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(RecordingService.this);
                        SharedPreferences.Editor edit = shared.edit();
                        edit.putString(CallApplication.PREFERENCE_LAST, Storage.getName(RecordingService.this, fly.targetUri));
                        edit.commit();

                        CallApplication.setContact(RecordingService.this, info.targetUri, info.contactId);
                        CallApplication.setCall(RecordingService.this, info.targetUri, info.call);
                    }
                };

                try {
                    long start = System.currentTimeMillis();
                    recorder.startRecording();

                    int samplesTimeCount = 0;
                    // how many samples we need to update 'samples'. time clock. every 1000ms.
                    int samplesTimeUpdate = 1000 * sampleRate / 1000;

                    short[] buffer = new short[100 * sampleRate / 1000 * DEFAULT_CHANNEL];

                    boolean stableRefresh = false;

                    while (!interrupt.get()) {
                        final int readSize = recorder.read(buffer, 0, buffer.length);
                        if (readSize < 0) {
                            break;
                        }
                        long end = System.currentTimeMillis();

                        long diff = (end - start) * sampleRate / 1000;

                        start = end;

                        int samples = readSize / DEFAULT_CHANNEL;

                        if (stableRefresh || diff >= samples) {
                            stableRefresh = true;

                            fly.encode(buffer, 0, readSize);

                            samplesTime += samples;
                            samplesTimeCount += samples;
                            if (samplesTimeCount > samplesTimeUpdate) {
                                samplesTimeCount -= samplesTimeUpdate;
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    Storage.delete(RecordingService.this, fly.targetUri);
                    Post(RecordingService.this, e);
                    return; // no save
                } finally {
                    wlcpu.release();

                    handle.post(done);

                    if (recorder != null)
                        recorder.release();

                    if (fly != null) {
                        try {
                            fly.close();
                        } catch (RuntimeException e) {
                            Storage.delete(RecordingService.this, fly.targetUri);
                            Post(RecordingService.this, e);
                            return; // no save
                        }
                    }
                }
                handle.post(save);
            }
        };
        thread.start();
    }

    RawSamples.Info getInfo() {
        return new RawSamples.Info(sampleRate, DEFAULT_CHANNEL);
    }

    void encoding(final File in, final Uri uri, final Runnable done, final Success success) {
        final OnFlyEncoding fly = new OnFlyEncoding(storage, uri, getInfo());

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(RecordingService.this);

        encoder = new FileEncoder(this, in, fly);


        final Runnable save = new Runnable() {
            @Override
            public void run() {
                Storage.delete(in); // delete raw recording


                SharedPreferences.Editor edit = shared.edit();
                edit.putString(CallApplication.PREFERENCE_LAST, Storage.getName(RecordingService.this, fly.targetUri));
                edit.commit();

                success.run(fly.targetUri);
                done.run();
                encodingNext();
            }
        };

        encoder.run(new Runnable() {
            @Override
            public void run() {  // progress
            }
        }, new Runnable() {
            @Override
            public void run() {  // success only call, done
                save.run();
            }
        }, new Runnable() {
            @Override
            public void run() { // error
                Storage.delete(RecordingService.this, fly.targetUri);
                Error(RecordingService.this, encoder.getException());
                done.run();
                handle.removeCallbacks(encodingNext);
                handle.postDelayed(encodingNext, RETRY_DELAY);
            }
        });
    }

    void pauseButton() {
        if (thread != null)
            stopRecording();
        else
            startRecording();
    }

    void stopRecording() {
        if (thread != null) {
            interrupt.set(true);
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    void begin(boolean wasRinging) {
        now = System.currentTimeMillis();
        targetUri = storage.getNewFile(now, phone, contact, call);
        if (encoder != null)
            encoder.pause();
        if (storage.recordingPending()) {
            RawSamples rs = new RawSamples(storage.getTempRecording());
            samplesTime = rs.getSamples();
        } else {
            samplesTime = 0;
        }
        startRecording();
    }

    void finish() {
        stopRecording();
        File tmp = storage.getTempRecording();
        if (tmp.exists() && tmp.length() > 0) {
            File parent = tmp.getParentFile();
            File in = Storage.getNextFile(parent, Storage.TMP_REC, null);
            Storage.move(tmp, in);
            mapTarget.put(in, new CallInfo(targetUri, phone, contact, contactId, call, now));
            if (encoder == null) // double finish()? skip
                encodingNext();
            else
                encoder.resume();
        } else { // if encoding failed, we will get no output file, hide notifications
            updateIcon(false);
        }
    }

    void encodingNext() {
        handle.removeCallbacks(encodingNext); // clean next
        if (encoder != null) // can be called twice, exit if alreay encoding
            return;
        if (thread != null) // currently recorindg
            return;
        final File inFile = storage.getTempNextRecording();
        if (inFile == null)
            return;
        if (!inFile.exists())
            return;
        if (inFile.length() == 0) {
            mapTarget.remove(inFile);
            Storage.delete(inFile);
            return;
        }
        CallInfo c = mapTarget.get(inFile);
        if (c == null) { // service restarted, additional info lost
            c = new CallInfo();
            c.now = inFile.lastModified();
            c.targetUri = storage.getNewFile(c.now, c.phone, c.contact, c.call);
        }
        targetUri = c.targetUri; // update notification encoding name
        final String contactId = c.contactId;
        final String call = c.call;
        final Uri targetUri = RecordingService.this.targetUri;
        encoding = new Runnable() { //  allways called when done
            @Override
            public void run() {
                updateIcon(false);
                encoding = null;
                encoder = null;
            }
        };
        updateIcon(true); // update status (encoding)
        Log.d(TAG, "Encoded " + inFile.getName() + " to " + Storage.getDisplayName(this, targetUri));
        encoding(inFile, targetUri, encoding, new Success() {
            @Override
            public void run(Uri t) { // called on success
                mapTarget.remove(inFile);
                CallApplication.setContact(RecordingService.this, t, contactId);
                CallApplication.setCall(RecordingService.this, t, call);
            }
        });
    }
}
