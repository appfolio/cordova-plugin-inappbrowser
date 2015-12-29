package org.apache.cordova.inappbrowser;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.ICordovaHttpAuthHandler;
import org.apache.cordova.LOG;
import org.json.JSONException;
import org.json.JSONObject;
import org.xwalk.core.JavascriptInterface;

public class InAppBrowserInternal extends CordovaPlugin {
    private static final String LOG_TAG = "InAppBrowserInternal";
    protected InAppBrowserEventHandler eventHandler;

    public interface InAppBrowserEventHandler {
        /**
         * Notify the host application that a page has started loading.
         *
         * @param newUrl    The url of the page.
         *
         * @return          Object to stop propagation or null
         */
        Object onPageStarted(String newUrl);

        /**
         * Notify the host application that a page has finished loading.
         *
         * @param url  The url of the page.
         *
         * @return      Object to stop propagation or null
         */
        Object onPageFinished(String url);

        /**
         * Notify the host application that there was an error loading the page.
         *
         * @param errorCode     The error code received.
         * @param description   Description of the error.
         * @param url           The url the error was received for.
         *
         * @return              Object to stop propagation or null
         */
        Object onReceivedError(int errorCode, String description, String url);

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
        Boolean onReceivedHttpAuthRequest(CordovaWebView view, ICordovaHttpAuthHandler handler, String host, String realm);

        /**
         * Notify the host application that the InAppBrowser is exiting.
         *
         * @return              Object to stop propagation or null
         */
        Object onExit();

        /**
         * Notify the host application that the internal onFinish callback was called
         *
         * @param message       The string the callback was called with
         * @param callbackId    The callback ID to send the result to
         */
        void onCallbackFinish(String message, String callbackId);
    }

    protected class InAppBrowserJsInterface {
        @JavascriptInterface
        public void onFinish(String message, String callbackId) {
            if (callbackId.startsWith("InAppBrowser") && eventHandler != null) {
                eventHandler.onCallbackFinish(message, callbackId);
            }
        }
    }

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    InAppBrowser.invokeOn(webView.getView(), "addJavascriptInterface", new Class[]{ Object.class, String.class }, new Object[] { new InAppBrowserJsInterface(), "__inappbrowser" });
                } catch (InAppBrowser.InvokeException e) {
                    throw new RuntimeException("Could not add Javascript interface:", e.getCause());
                }
            }
        });
    }

    @Override
    public Boolean shouldAllowRequest(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        } else {
            return super.shouldAllowRequest(url);
        }
    }

    @Override
    public Boolean shouldAllowNavigation(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            if (eventHandler != null) {
                eventHandler.onPageStarted(url);
            }

            return true;
        } else if (url.startsWith(WebView.SCHEME_TEL)) {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse(url));
                cordova.getActivity().startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
            }

            return false;
        } else if (url.startsWith("geo:") || url.startsWith(WebView.SCHEME_MAILTO) || url.startsWith("market:")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                cordova.getActivity().startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                LOG.e(LOG_TAG, "Error with " + url + ": " + e.toString());
            }

            return false;
        } else if (url.startsWith("sms:")) { // If sms:5551212?body=This is the message
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);

                // Get address
                String address = null;
                int parmIndex = url.indexOf('?');
                if (parmIndex == -1) {
                    address = url.substring(4);
                }
                else {
                    address = url.substring(4, parmIndex);

                    // If body, then set sms body
                    Uri uri = Uri.parse(url);
                    String query = uri.getQuery();
                    if (query != null) {
                        if (query.startsWith("body=")) {
                            intent.putExtra("sms_body", query.substring(5));
                        }
                    }
                }
                intent.setData(Uri.parse("sms:" + address));
                intent.putExtra("address", address);
                intent.setType("vnd.android-dir/mms-sms");
                cordova.getActivity().startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                LOG.e(LOG_TAG, "Error sending sms " + url + ":" + e.toString());
            }

            return false;
        } else {
            return super.shouldAllowNavigation(url);
        }
    }

    @Override
    public Boolean shouldAllowBridgeAccess(String url) {
        return false;
    }

    @Override
    public boolean onReceivedHttpAuthRequest(CordovaWebView view, ICordovaHttpAuthHandler handler, String host, String realm) {
        if (eventHandler != null) {
            return eventHandler.onReceivedHttpAuthRequest(view, handler, host, realm);
        } else {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object onMessage(String id, Object data) {
        if (eventHandler == null) {
            return null;
        }

        if (id.equals("onPageFinished") && data instanceof String) {
            return eventHandler.onPageFinished((String)data);
        } else if (id.equals("onReceivedError") && data instanceof JSONObject) {
            try {
                JSONObject jsonObject = (JSONObject)data;
                return eventHandler.onReceivedError(jsonObject.getInt("errorCode"), jsonObject.getString("description"), jsonObject.getString("url"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (id.equals("exit")) {
            return eventHandler.onExit();
        }

        return null;
    }

    public void setEventHandler(InAppBrowserEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }
}
