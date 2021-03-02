/*global cordova, module*/

module.exports = {
    greet: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "Hello", "greet", []);
    }
};
