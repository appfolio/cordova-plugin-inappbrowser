package org.apache.cordova.inappbrowser;

import android.webkit.JavascriptInterface;

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
}
