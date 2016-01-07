package org.apache.cordova.inappbrowser;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;

public interface InAppBrowser {
    String NULL = "null";
    String SELF = "_self";
    String SYSTEM = "_system";
    String EXIT_EVENT = "exit";
    String LOCATION = "location";
    String ZOOM = "zoom";
    String HIDDEN = "hidden";
    String LOAD_START_EVENT = "loadstart";
    String LOAD_STOP_EVENT = "loadstop";
    String LOAD_ERROR_EVENT = "loaderror";
    String CLEAR_ALL_CACHE = "clearcache";
    String CLEAR_SESSION_CACHE = "clearsessioncache";
    String HARDWARE_BACK_BUTTON = "hardwareback";

    InAppBrowser getInAppBrowser();
    CordovaInterface getCordovaInterface();
    CordovaPreferences getPreferences();
    CordovaWebView getWebView();
    InAppBrowserDriver getDriver();

    boolean getShowLocationBar();
    boolean getShowZoomControls();
    boolean getOpenWindowHidden();
    boolean getClearAllCache();
    boolean getClearSessionCache();
    boolean getHardwareBackButton();

    /**
     * Create a new plugin success result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     */
    void sendUpdate(JSONObject obj, boolean keepCallback);

    /**
     * Create a new plugin result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     * @param status the status code to return to the JavaScript environment
     */
    void sendUpdate(JSONObject obj, boolean keepCallback, PluginResult.Status status);
}
