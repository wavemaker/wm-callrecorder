/*global cordova, module*/

module.exports = {
    startRecordingService: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Callrecord", "startRecordingService", []);
    }
};
