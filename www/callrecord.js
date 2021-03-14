/*global cordova, module*/

module.exports = {
    startRecordingService: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "startRecordingService", []);
    },
    openAccessibility: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "openAccessibility", []);
    },
    accessibilityStatus: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "accessibilityStatus", []);
    }
};
