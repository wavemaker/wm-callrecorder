wm-callrecorder
=========================
Cordova plugin that allows to record Voice calls, This plugin supports only Android


- [How does it work](#how-does-it-works)
- [Caveat](#caveat)
- [Setup](#setup)
- [Sample APK](#APK)



# How does it works
Until Android 9, Android provides API to capture the Voice calls (`MediaRecorder.AudioSource.VOICE_CALL`)

 From Android 10 & 11, Capturing voice call are restricted, So we will register our app as Accessibility voice provider (`MediaRecorder.AudioSource.VOICE_RECOGNITION`) and we’ll record the calls.

# Caveat
In Android 10, In some devices we will not be able to record the caller’s voice 

* If Device has active inbuilt call recording feature, In this case the built-in app will have privileged(`permission.CAPTURE_AUDIO_HOTWORD`) permissions so our will not be able to capture the voice calls.
* If the manufacturer had used `setPrivacySensitive()` is `true` in audio sources, Then only our voice will be recorded and opponents voice will not be recorded. (Devices like Realme pro 3 pro, Realme 7)

# Setup


## 1. Start Service

    var success = function(message) {
        alert("Recording Enabled");
    }

    var failure = function() {
        alert("Error calling Plugin");
    }

    cordova.plugins.callrecord.startRecordingService(success, failure);


## 2. Open Accessibility Service

    var success = function() {
        alert("Accessibility Opened");
    }

    var failure = function() {
        alert("Error calling Plugin");
    }

    cordova.plugins.callrecord.openAccessibility(success, failure);

## 3. Open AppSetting

    var success = function() {
        alert("AppSetting Opened");
    }

    var failure = function() {
        alert("Error calling Plugin");
    }

    cordova.plugins.callrecord.openAppSetting(success, failure);

## 4. Exclude Power Saver Service

    var success = function() {
        alert("Excluded Power Saver");
    }

    var failure = function() {
        alert("Error calling Plugin");
    }

    cordova.plugins.callrecord.excludePowerSaver(success, failure);

## 5. IsExclude Power Saver Service

    var success = function(flag) { //boolean flag
        alert("Excluded Power Saver status" + flag);
    }

    var failure = function() {
        alert("Error calling Plugin");
    }

    cordova.plugins.callrecord.IsIgnoringBatteryOptimizations(success, failure);


# Sample APK
https://www.wavemakeronline.com/file-service/40beee9803b14d89a779653145cd7bae 

## License
Apache License - 2.0