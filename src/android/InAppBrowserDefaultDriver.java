package org.apache.cordova.inappbrowser;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;

public class InAppBrowserDefaultDriver extends InAppBrowserDriver {
    public class InAppBrowserJavascriptInterfaceImpl implements InAppBrowserJavascriptInterface {
        @JavascriptInterface
        public void onFinish(String message, String callbackId) {
            if (callbackId.startsWith("InAppBrowser")) {
                onCallbackFinish(message, callbackId);
            }
        }
    }

    public InAppBrowserDefaultDriver(InAppBrowser inAppBrowser, String url) {
        super(inAppBrowser, url);
    }

    @Override
    public InAppBrowserJavascriptInterface getJavascriptInterface() {
        return new InAppBrowserJavascriptInterfaceImpl();
    }

    @Override
    public void setSettingsOnWebView() {
        WebSettings settings = null;
        try {
            settings = (WebSettings)InAppBrowserImpl.invokeOn(inAppWebView.getView(), "getSettings", new Class[0], new Object[0]);
        } catch (InAppBrowserImpl.InvokeException e) {
            e.printStackTrace();
        }

        if (settings != null) {
            settings.setBuiltInZoomControls(inAppBrowser.getShowZoomControls());

            // Toggle whether this is enabled or not!
            Bundle appSettings = inAppBrowser.getCordovaInterface().getActivity().getIntent().getExtras();
            boolean enableDatabase = appSettings == null || appSettings.getBoolean("InAppBrowserStorageEnabled", true);
            settings.setDatabaseEnabled(enableDatabase);
        }

        if (inAppBrowser.getClearAllCache()) {
            CookieManager.getInstance().removeAllCookie();
        } else if (inAppBrowser.getClearSessionCache()) {
            CookieManager.getInstance().removeSessionCookie();
        }
    }
}
