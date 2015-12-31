package org.apache.cordova.inappbrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPreferences;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaWebViewImpl;
import org.apache.cordova.ICordovaHttpAuthHandler;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public abstract class InAppBrowserDriver {
    private static final String LOG_TAG = "InAppBrowserDriver";

    protected InAppBrowser inAppBrowser;
    protected CordovaPreferences preferences;
    protected CordovaWebViewImpl inAppWebView;
    protected InAppBrowserDialog dialog;
    protected EditText edittext;
    protected String url;

    public InAppBrowserDriver(InAppBrowser inAppBrowser, String url) {
        this.inAppBrowser = inAppBrowser;
        this.preferences = new CordovaPreferences();
        this.url = url;
    }

    abstract public InAppBrowserJavascriptInterface getJavascriptInterface();

    /**
     * Notify the host application that a page has started loading.
     *
     * @param newUrl    The url of the page.
     *
     * @return          Object to stop propagation or null
     */
    public Object onPageStarted(String newUrl) {
        if (!newUrl.equals(edittext.getText().toString())) {
            edittext.setText(newUrl);
        }

        try {
            JSONObject obj = new JSONObject();
            obj.put("type", InAppBrowser.LOAD_START_EVENT);
            obj.put("url", newUrl);

            inAppBrowser.sendUpdate(obj, true);
        } catch (JSONException ex) {
            Log.d(LOG_TAG, "Should never happen");
        }

        return null;
    }

    /**
     * Notify the host application that a page has finished loading.
     *
     * @param url  The url of the page.
     *
     * @return      Object to stop propagation or null
     */
    public Object onPageFinished(String url) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", InAppBrowser.LOAD_STOP_EVENT);
            obj.put("url", url);

            inAppBrowser.sendUpdate(obj, true);
        } catch (JSONException ex) {
            Log.d(LOG_TAG, "Should never happen");
        }

        return null;
    }

    /**
     * Notify the host application that there was an error loading the page.
     *
     * @param errorCode     The error code received.
     * @param description   Description of the error.
     * @param url           The url the error was received for.
     *
     * @return              Object to stop propagation or null
     */
    public Object onReceivedError(int errorCode, String description, String url) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", InAppBrowser.LOAD_ERROR_EVENT);
            obj.put("url", url);
            obj.put("code", errorCode);
            obj.put("message", description);

            inAppBrowser.sendUpdate(obj, true, PluginResult.Status.ERROR);
        } catch (JSONException ex) {
            Log.d(LOG_TAG, "Should never happen");
        }

        // prevent the default behavior
        return new Object();
    }

    /**
     * Notify the host application that there was a HTTP auth challenge issued.
     *
     * @param view      The WebView that is initiating the callback
     * @param handler   The HttpAuthHandler used to set the WebView's response
     * @param host      The host requiring authentication
     * @param realm     The realm for which authentication is required
     *
     * @return          Returns true if host application will resolve this auth challenge, otherwise false
     */
    public Boolean onReceivedHttpAuthRequest(CordovaWebView view, ICordovaHttpAuthHandler handler, String host, String realm) {
        // Check if there is some plugin which can resolve this auth challenge
        PluginManager pluginManager = null;
        try {
            pluginManager = (PluginManager)InAppBrowserImpl.invokeOn(inAppBrowser.getWebView().getView(), "getPluginManager", new Class[0], new Object[0]);
        } catch (InAppBrowserImpl.InvokeException e) {
        }

        if (pluginManager != null) {
            return pluginManager.onReceivedHttpAuthRequest(view, handler, host, realm);
        } else {
            return false;
        }
    }

    /**
     * Notify the host application that the InAppBrowserImpl is exiting.
     *
     * @return              Object to stop propagation or null
     */
    public Object onExit() {
        closeDialog();
        return new Object();
    }

    /**
     * Notify the host application that the internal onFinish callback was called
     *
     * @param message       The string the callback was called with
     * @param callbackId    The callback ID to send the result to
     */
    public void onCallbackFinish(String message, String callbackId) {
        PluginResult scriptResult;

        if (message == null || message.length() == 0) {
            scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray());
        } else {
            try {
                scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray(message));
            } catch(JSONException e) {
                scriptResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
            }
        }
        inAppBrowser.getWebView().sendPluginResult(scriptResult, callbackId);
    }

    /**
     * Inject an object (script or style) into the InAppBrowserImpl WebView.
     *
     * This is a helper method for the inject{Script|Style}{Code|File} API calls, which
     * provides a consistent method for injecting JavaScript code into the document.
     *
     * If a wrapper string is supplied, then the source string will be JSON-encoded (adding
     * quotes) and wrapped using string formatting. (The wrapper string should have a single
     * '%s' marker)
     *
     * @param source      The source object (filename or script/style text) to inject into
     *                    the document.
     * @param jsWrapper   A JavaScript string to wrap the source string in, so that the object
     *                    is properly injected, or null if the source string is JavaScript text
     *                    which should be executed directly.
     */
    public void injectDeferredObject(String source, String jsWrapper) {
        String scriptToInject;
        if (jsWrapper != null) {
            org.json.JSONArray jsonEsc = new org.json.JSONArray();
            jsonEsc.put(source);
            String jsonRepr = jsonEsc.toString();
            String jsonSourceString = jsonRepr.substring(1, jsonRepr.length() - 1);
            scriptToInject = String.format(jsWrapper, jsonSourceString);
        } else {
            scriptToInject = source;
        }
        final String finalScriptToInject = scriptToInject;
        inAppBrowser.getCordova().getActivity().runOnUiThread(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    // This action will have the side-effect of blurring the currently focused element
                    inAppWebView.loadUrl("javascript:" + finalScriptToInject);
                } else {
                    try {
                        InAppBrowserImpl.invokeOn(inAppWebView.getView(), "evaluateJavascript", new Class[]{String.class, ValueCallback.class}, new Object[]{finalScriptToInject, null});
                    } catch (InAppBrowserImpl.InvokeException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void initWebView() {
        final CordovaInterface cordova = inAppBrowser.getCordova();

        // Create CordovaWebViewImpl
        inAppWebView = new CordovaWebViewImpl(CordovaWebViewImpl.createEngine(cordova.getActivity(), preferences));
        inAppWebView.getView().setLayoutParams(new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT));

        // Crosswalk does not have getSettings and XWalkPreferences does not have any of the needed settings
        if (inAppWebView.getView() instanceof WebView) {
            WebSettings settings = null;
            try {
                settings = (WebSettings)InAppBrowserImpl.invokeOn(inAppWebView.getView(), "getSettings", new Class[0], new Object[0]);
            } catch (InAppBrowserImpl.InvokeException e) {
                e.printStackTrace();
            }

            if (settings != null) {
                settings.setBuiltInZoomControls(inAppBrowser.getShowZoomControls());

                // Toggle whether this is enabled or not!
                Bundle appSettings = cordova.getActivity().getIntent().getExtras();
                boolean enableDatabase = appSettings == null || appSettings.getBoolean("InAppBrowserStorageEnabled", true);
                settings.setDatabaseEnabled(enableDatabase);
            }

            if (inAppBrowser.getClearAllCache()) {
                CookieManager.getInstance().removeAllCookie();
            } else if (inAppBrowser.getClearSessionCache()) {
                CookieManager.getInstance().removeSessionCookie();
            }
        }

        if (!inAppWebView.isInitialized()) {
            ArrayList<PluginEntry> pluginEntries = new ArrayList<PluginEntry>();
            String inAppBrowserInternalPlugin = inAppBrowser.getPreferences().getString("inAppBrowserInternalPlugin", InAppBrowserInternal.class.getCanonicalName());
            pluginEntries.add(new PluginEntry("InAppBrowserInternal", inAppBrowserInternalPlugin, true));
            inAppWebView.init(cordova, pluginEntries, preferences);

            InAppBrowserInternal internalPlugin = getInAppBrowserInternalPlugin();
            if (internalPlugin != null) {
                internalPlugin.init(this);
            }
        }

        inAppWebView.loadUrl(url);
        inAppWebView.handleResume(true);
    }

    public void createViews() {
        final CordovaInterface cordova = inAppBrowser.getCordova();

        // Let's create the main dialog
        dialog = new InAppBrowserDialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
        dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setInAppBrowserImpl(inAppBrowser);

        // Main container layout
        LinearLayout main = new LinearLayout(cordova.getActivity());
        main.setOrientation(LinearLayout.VERTICAL);

        // Toolbar layout
        RelativeLayout toolbar = new RelativeLayout(cordova.getActivity());
        //Please, no more black!
        toolbar.setBackgroundColor(android.graphics.Color.LTGRAY);
        toolbar.setLayoutParams(new RelativeLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dpToPixels(44)));
        toolbar.setPadding(dpToPixels(2), dpToPixels(2), dpToPixels(2), dpToPixels(2));
        toolbar.setHorizontalGravity(Gravity.LEFT);
        toolbar.setVerticalGravity(Gravity.TOP);

        // Action Button Container layout
        RelativeLayout actionButtonContainer = new RelativeLayout(cordova.getActivity());
        actionButtonContainer.setLayoutParams(new RelativeLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT));
        actionButtonContainer.setHorizontalGravity(Gravity.LEFT);
        actionButtonContainer.setVerticalGravity(Gravity.CENTER_VERTICAL);
        actionButtonContainer.setId(1);

        // Back button
        Button back = new Button(cordova.getActivity());
        RelativeLayout.LayoutParams backLayoutParams = new RelativeLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.MATCH_PARENT);
        backLayoutParams.addRule(RelativeLayout.ALIGN_LEFT);
        back.setLayoutParams(backLayoutParams);
        back.setContentDescription("Back Button");
        back.setId(2);
        Resources activityRes = cordova.getActivity().getResources();
        int backResId = activityRes.getIdentifier("ic_action_previous_item", "drawable", cordova.getActivity().getPackageName());
        Drawable backIcon = activityRes.getDrawable(backResId);
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN)
        {
            back.setBackgroundDrawable(backIcon);
        }
        else
        {
            back.setBackground(backIcon);
        }
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goBack();
            }
        });

        // Forward button
        Button forward = new Button(cordova.getActivity());
        RelativeLayout.LayoutParams forwardLayoutParams = new RelativeLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.MATCH_PARENT);
        forwardLayoutParams.addRule(RelativeLayout.RIGHT_OF, 2);
        forward.setLayoutParams(forwardLayoutParams);
        forward.setContentDescription("Forward Button");
        forward.setId(3);
        int fwdResId = activityRes.getIdentifier("ic_action_next_item", "drawable", cordova.getActivity().getPackageName());
        Drawable fwdIcon = activityRes.getDrawable(fwdResId);
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN)
        {
            forward.setBackgroundDrawable(fwdIcon);
        }
        else
        {
            forward.setBackground(fwdIcon);
        }
        forward.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goForward();
            }
        });

        // Edit Text Box
        edittext = new EditText(cordova.getActivity());
        RelativeLayout.LayoutParams textLayoutParams = new RelativeLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        textLayoutParams.addRule(RelativeLayout.RIGHT_OF, 1);
        textLayoutParams.addRule(RelativeLayout.LEFT_OF, 5);
        edittext.setLayoutParams(textLayoutParams);
        edittext.setId(4);
        edittext.setSingleLine(true);
        edittext.setText(url);
        edittext.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        edittext.setImeOptions(EditorInfo.IME_ACTION_GO);
        edittext.setInputType(InputType.TYPE_NULL); // Will not except input... Makes the text NON-EDITABLE
        edittext.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    navigate(edittext.getText().toString());
                    return true;
                }
                return false;
            }
        });

        // Close/Done button
        Button close = new Button(cordova.getActivity());
        RelativeLayout.LayoutParams closeLayoutParams = new RelativeLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.MATCH_PARENT);
        closeLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        close.setLayoutParams(closeLayoutParams);
        forward.setContentDescription("Close Button");
        close.setId(5);
        int closeResId = activityRes.getIdentifier("ic_action_remove", "drawable", cordova.getActivity().getPackageName());
        Drawable closeIcon = activityRes.getDrawable(closeResId);
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN)
        {
            close.setBackgroundDrawable(closeIcon);
        }
        else
        {
            close.setBackground(closeIcon);
        }
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeDialog();
            }
        });

        inAppWebView.getView().setId(6);
        inAppWebView.getView().requestFocus();
        inAppWebView.getView().requestFocusFromTouch();

        // Add the back and forward buttons to our action button container layout
        actionButtonContainer.addView(back);
        actionButtonContainer.addView(forward);

        // Add the views to our toolbar
        toolbar.addView(actionButtonContainer);
        toolbar.addView(edittext);
        toolbar.addView(close);

        // Don't add the toolbar if its been disabled
        if (inAppBrowser.getShowLocationBar()) {
            // Add our toolbar to our main view/layout
            main.addView(toolbar);
        }

        // Add our webview to our main view/layout
        main.addView(inAppWebView.getView());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(dialog.getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;

        dialog.setContentView(main);
        dialog.show();
        dialog.getWindow().setAttributes(lp);

        // the goal of openhidden is to load the url and not display it
        // Show() needs to be called to cause the URL to be loaded
        if (inAppBrowser.getOpenWindowHidden()) {
            dialog.hide();
        }
    }

    /**
     * Shows the dialog
     */
    public void showDialog() {
        inAppBrowser.getCordova().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (dialog != null) {
                    dialog.show();
                }
            }
        });
    }

    /**
     * Closes the dialog
     */
    public void closeDialog() {
        final CordovaWebView childView = inAppWebView;
        // The JS protects against multiple calls, so this should happen only when
        // closeDialog() is called by other native code.
        if (childView == null) {
            return;
        }
        inAppBrowser.getCordova().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // NB: From SDK 19: "If you call methods on WebView from any thread
                // other than your app's UI thread, it can cause unexpected results."
                // http://developer.android.com/guide/webapps/migrating.html#Threads
                childView.handleDestroy();
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });

        try {
            JSONObject obj = new JSONObject();
            obj.put("type", InAppBrowser.EXIT_EVENT);
            inAppBrowser.sendUpdate(obj, false);
        } catch (JSONException ex) {
            Log.d(LOG_TAG, "Should never happen");
        }
    }

    protected InAppBrowserInternal getInAppBrowserInternalPlugin() {
        if (inAppWebView != null) {
            return (InAppBrowserInternal)inAppWebView.getPluginManager().getPlugin("InAppBrowserInternal");
        }
        return null;
    }

    /**
     * Checks to see if it is possible to go back one page in history, then does so.
     */
    public void goBack() {
        if (canGoBack()) {
            inAppWebView.getEngine().goBack();
        }
    }

    /**
     * Can the web browser go back?
     * @return boolean
     */
    public boolean canGoBack() {
        return inAppWebView.canGoBack();
    }

    /**
     * Checks to see if it is possible to go forward one page in history, then does so.
     */
    public void goForward() {
        try {
            if ((Boolean)InAppBrowserImpl.invokeOn(inAppWebView.getView(), "canGoForward", new Class[0], new Object[0])) {
                InAppBrowserImpl.invokeOn(inAppWebView.getView(), "goForward", new Class[0], new Object[0]);
            }
        } catch (InAppBrowserImpl.InvokeException e) {
            e.printStackTrace();
        }
    }

    /**
     * Navigate to the new page
     *
     * @param url to load
     */
    private void navigate(String url) {
        InputMethodManager imm = (InputMethodManager)inAppBrowser.getCordova().getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(edittext.getWindowToken(), 0);

        if (!url.startsWith("http") && !url.startsWith("file:")) {
            this.inAppWebView.loadUrl("http://" + url);
        } else {
            this.inAppWebView.loadUrl(url);
        }
        this.inAppWebView.getView().requestFocus();
    }

    /**
     * Convert our DIP units to Pixels
     *
     * @return int
     */
    protected int dpToPixels(int dipValue) {
        int value = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                (float) dipValue,
                inAppBrowser.getCordova().getActivity().getResources().getDisplayMetrics()
        );

        return value;
    }
}
