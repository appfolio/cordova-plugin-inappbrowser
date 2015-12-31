/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.inappbrowser;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaWebViewImpl;

import android.content.Intent;
import android.provider.Browser;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.StringTokenizer;

public class InAppBrowserImpl extends CordovaPlugin implements InAppBrowser {

    private static final String LOG_TAG = "InAppBrowserImpl";

    private InAppBrowserDriver driver;
    private CordovaWebViewImpl inAppWebView;
    private CallbackContext callbackContext;
    private boolean showLocationBar;
    private boolean showZoomControls;
    private boolean openWindowHidden;
    private boolean clearAllCache;
    private boolean clearSessionCache;
    private boolean hardwareBackButton;

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action the action to execute.
     * @param args JSONArry of arguments for the plugin.
     * @param callbackContext the callbackContext used when calling back into JavaScript.
     * @return A PluginResult object with a status and message.
     */
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("open")) {
            this.callbackContext = callbackContext;
            final String url = args.getString(0);
            String t = args.optString(1);
            if (t == null || t.equals("") || t.equals(NULL)) {
                t = SELF;
            }
            final String target = t;
            final HashMap<String, Boolean> features = parseFeature(args.optString(2));

            Log.d(LOG_TAG, "target = " + target);

            this.cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String result = "";
                    // SELF
                    if (SELF.equals(target)) {
                        Log.d(LOG_TAG, "in self");
                        /* This code exists for compatibility between 3.x and 4.x versions of Cordova.
                         * Previously the Config class had a static method, isUrlWhitelisted(). That
                         * responsibility has been moved to the plugins, with an aggregating method in
                         * PluginManager.
                         */
                        Boolean shouldAllowNavigation = null;
                        if (url.startsWith("javascript:")) {
                            shouldAllowNavigation = true;
                        }
                        if (shouldAllowNavigation == null) {
                            try {
                                Method iuw = Config.class.getMethod("isUrlWhiteListed", String.class);
                                shouldAllowNavigation = (Boolean)iuw.invoke(null, url);
                            } catch (NoSuchMethodException e) {
                            } catch (IllegalAccessException e) {
                            } catch (InvocationTargetException e) {
                            }
                        }
                        if (shouldAllowNavigation == null) {
                            try {
                                PluginManager pm = (PluginManager)invokeOn(webView.getView(), "getPluginManager", new Class[0], new Object[0]);
                                shouldAllowNavigation = (Boolean)invokeOn(pm, "shouldAllowNavigation", new Class[]{String.class}, new Object[]{url});
                            } catch (InvokeException e) {
                            }
                        }
                        // load in webview
                        if (Boolean.TRUE.equals(shouldAllowNavigation)) {
                            Log.d(LOG_TAG, "loading in webview");
                            webView.loadUrl(url);
                        }
                        //Load the dialer
                        else if (url.startsWith(WebView.SCHEME_TEL))
                        {
                            try {
                                Log.d(LOG_TAG, "loading in dialer");
                                Intent intent = new Intent(Intent.ACTION_DIAL);
                                intent.setData(Uri.parse(url));
                                cordova.getActivity().startActivity(intent);
                            } catch (android.content.ActivityNotFoundException e) {
                                LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
                            }
                        }
                        // load in InAppBrowserImpl
                        else {
                            Log.d(LOG_TAG, "loading in InAppBrowserImpl");
                            result = showWebPage(url, features);
                        }
                    }
                    // SYSTEM
                    else if (SYSTEM.equals(target)) {
                        Log.d(LOG_TAG, "in system");
                        result = openExternal(url);
                    }
                    // BLANK - or anything else
                    else {
                        Log.d(LOG_TAG, "in blank");
                        result = showWebPage(url, features);
                    }

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                }
            });
        }
        else if (action.equals("close")) {
            driver.closeDialog();
        }
        else if (action.equals("injectScriptCode")) {
            String jsWrapper = null;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("__inappbrowser.onFinish(JSON.stringify([eval(%%s)]), '%s')", callbackContext.getCallbackId());
            }
            driver.injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectScriptFile")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('script'); c.src = %%s; c.onload = function() { __inappbrowser.onFinish('', '%s'); }; d.body.appendChild(c); })(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)";
            }
            driver.injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectStyleCode")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('style'); c.innerHTML = %%s; d.body.appendChild(c); __inappbrowser.onFinish('', '%s');})(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %s; d.body.appendChild(c); })(document)";
            }
            driver.injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("injectStyleFile")) {
            String jsWrapper;
            if (args.getBoolean(1)) {
                jsWrapper = String.format("(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%s; d.head.appendChild(c); __inappbrowser.onFinish('', '%s');})(document)", callbackContext.getCallbackId());
            } else {
                jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %s; d.head.appendChild(c); })(document)";
            }
            driver.injectDeferredObject(args.getString(0), jsWrapper);
        }
        else if (action.equals("show")) {
            driver.showDialog();
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            pluginResult.setKeepCallback(true);
            this.callbackContext.sendPluginResult(pluginResult);
        }
        else {
            return false;
        }
        return true;
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        driver.closeDialog();
    }

    /**
     * Called by AccelBroker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        driver.closeDialog();
    }

    public InAppBrowser getInAppBrowser() {
        return this;
    }

    public CordovaInterface getCordova() {
        return cordova;
    }

    public CordovaPreferences getPreferences() {
        return preferences;
    }

    public CordovaWebView getWebView() {
        return webView;
    }

    public InAppBrowserDriver getDriver() {
        return driver;
    }

    /**
     * Should we show the location bar?
     * @return boolean
     */
    public boolean getShowLocationBar() {
        return this.showLocationBar;
    }

    /**
     * Should we show the zoom controls?
     * @return boolean
     */
    public boolean getShowZoomControls() {
        return this.showZoomControls;
    }

    /**
     * Should we open the window hidden?
     * @return boolean
     */
    public boolean getOpenWindowHidden() {
        return this.openWindowHidden;
    }

    /**
     * Should we clear all cookies?
     * @return boolean
     */
    public boolean getClearAllCache() {
        return this.clearAllCache;
    }

    /**
     * Should we clear session cookies?
     * @return boolean
     */
    public boolean getClearSessionCache() {
        return this.clearSessionCache;
    }

    /**
     * Should we use the hardware button to go back?
     * @return boolean
     */
    public boolean getHardwareBackButton() {
        return hardwareBackButton;
    }

    /**
     * Put the list of features into a hash map
     *
     * @param optString
     * @return
     */
    private HashMap<String, Boolean> parseFeature(String optString) {
        if (optString.equals(NULL)) {
            return null;
        } else {
            HashMap<String, Boolean> map = new HashMap<String, Boolean>();
            StringTokenizer features = new StringTokenizer(optString, ",");
            StringTokenizer option;
            while(features.hasMoreElements()) {
                option = new StringTokenizer(features.nextToken(), "=");
                if (option.hasMoreElements()) {
                    String key = option.nextToken();
                    Boolean value = option.nextToken().equals("no") ? Boolean.FALSE : Boolean.TRUE;
                    map.put(key, value);
                }
            }
            return map;
        }
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @return "" if ok, or error message.
     */
    public String openExternal(String url) {
        try {
            Intent intent = null;
            intent = new Intent(Intent.ACTION_VIEW);
            // Omitting the MIME type for file: URLs causes "No Activity found to handle Intent".
            // Adding the MIME type to http: URLs causes them to not be handled by the downloader.
            Uri uri = Uri.parse(url);
            if ("file".equals(uri.getScheme())) {
                intent.setDataAndType(uri, webView.getResourceApi().getMimeType(uri));
            } else {
                intent.setData(uri);
            }
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, cordova.getActivity().getPackageName());
            this.cordova.getActivity().startActivity(intent);
            return "";
        } catch (android.content.ActivityNotFoundException e) {
            Log.d(LOG_TAG, "InAppBrowserImpl: Error loading url "+url+":"+ e.toString());
            return e.toString();
        }
    }

    /**
     * Display a new browser with the specified URL.
     *
     * @param url the url to load.
     * @param features jsonObject
     */
    public String showWebPage(final String url, HashMap<String, Boolean> features) {
        showLocationBar = true;
        showZoomControls = true;
        openWindowHidden = false;
        clearAllCache = false;
        clearSessionCache = false;
        hardwareBackButton = true;
        if (features != null) {
            Boolean show = features.get(LOCATION);
            if (show != null) {
                showLocationBar = show.booleanValue();
            }
            Boolean zoom = features.get(ZOOM);
            if (zoom != null) {
                showZoomControls = zoom.booleanValue();
            }
            Boolean hidden = features.get(HIDDEN);
            if (hidden != null) {
                openWindowHidden = hidden.booleanValue();
            }
            Boolean hardwareBack = features.get(HARDWARE_BACK_BUTTON);
            if (hardwareBack != null) {
                hardwareBackButton = hardwareBack.booleanValue();
            }
            Boolean cache = features.get(CLEAR_ALL_CACHE);
            if (cache != null) {
                clearAllCache = cache.booleanValue();
            } else {
                cache = features.get(CLEAR_SESSION_CACHE);
                if (cache != null) {
                    clearSessionCache = cache.booleanValue();
                }
            }
        }

        // Create driver which creates the views and loads up the webview on a UI thread
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String className = preferences.getString("inAppBrowserDriver", InAppBrowserDefaultDriver.class.getCanonicalName());
                try {
                    Class<?> driverClass = Class.forName(className);
                    Constructor<?> constructor = driverClass.getConstructor(InAppBrowser.class, String.class);
                    driver = (InAppBrowserDriver) constructor.newInstance(getInAppBrowser(), url);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create InAppBrowserDriver. ", e);
                }

                driver.initWebView();
                driver.createViews();
            }
        });
        return "";
    }

    /**
     * Create a new plugin success result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     */
    public void sendUpdate(JSONObject obj, boolean keepCallback) {
        sendUpdate(obj, keepCallback, PluginResult.Status.OK);
    }

    /**
     * Create a new plugin result and send it back to JavaScript
     *
     * @param obj a JSONObject contain event payload information
     * @param status the status code to return to the JavaScript environment
     */
    public void sendUpdate(JSONObject obj, boolean keepCallback, PluginResult.Status status) {
        if (callbackContext != null) {
            PluginResult result = new PluginResult(status, obj);
            result.setKeepCallback(keepCallback);
            callbackContext.sendPluginResult(result);
            if (!keepCallback) {
                callbackContext = null;
            }
        }
    }

    protected static class InvokeException extends Exception {
        public InvokeException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }

    /**
     * Invoke a method on any object.
     *
     * This is a helper method to be able to call methods without regard for the type
     * This is so we can easily support either the system WebView or Crosswalk, or potentially
     * other engines that implement the same methods we use.
     *
     * @param object            The object to invoke the method on
     * @param methodName        The name of the method to call
     * @param parameterTypes    An array of the parameter types of the method
     * @param args              An array of the arguments to pass to the method
     * @return                  The result of the invoked method
     */
    public static Object invokeOn(Object object, String methodName, Class[] parameterTypes, Object[] args) throws InvokeException {
        try {
            Method methodOnWebView = object.getClass().getMethod(methodName, parameterTypes);
            return methodOnWebView.invoke(object, args);
        } catch (NoSuchMethodException e) {
            throw new InvokeException("Could not invoke on object:", e.getCause());
        } catch (InvocationTargetException e) {
            throw new InvokeException("Could not invoke on object:", e.getCause());
        } catch (IllegalAccessException e) {
            throw new InvokeException("Could not invoke on object:", e.getCause());
        }
    }
}
