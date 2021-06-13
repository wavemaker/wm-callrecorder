/*global cordova, module*/

var exec = require('cordova/exec');
var cordova = require('cordova');
var channel = require('cordova/channel');

var Record = function () {
};  

Record.prototype.startRecordingService = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "Callrecord", "startRecordingService", []);
};
Record.prototype.openAccessibility = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "Callrecord", "openAccessibility", []);
};
Record.prototype.openAppSetting = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "Callrecord", "openAppSetting", []);
};
Record.prototype.excludePowerSaver = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "Callrecord", "excludePowerSaver", []);
};
Record.prototype.IsIgnoringBatteryOptimizations = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "Callrecord", "IsIgnoringBatteryOptimizations", []);
};
Record.prototype.fileAvailable = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, "Callrecord", "fileAvailable", []);
};

var record = new Record();

channel.onCordovaReady.subscribe(function () {
    record.fileAvailable(function (info) {
        cordova.fireDocumentEvent('fileAvailable'); 
    },
    function (e) {
        console.log('Error initializing Network Connection: ' + e);
    });
});

module.exports = record;