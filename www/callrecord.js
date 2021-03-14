/*global cordova, module*/

module.exports = {
    startRecordingService: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "startRecordingService", []);
    },
    openAccessibility: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "openAccessibility", []);
    },
    openAppSetting: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "openAppSetting", []);
    },
    excludePowerSaver: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "excludePowerSaver", []);
    },
    IsIgnoringBatteryOptimizations: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "IsIgnoringBatteryOptimizations", []);
    }
};
