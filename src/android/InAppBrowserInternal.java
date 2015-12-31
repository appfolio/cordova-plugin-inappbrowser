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

public class InAppBrowserInternal extends CordovaPlugin {
    private static final String LOG_TAG = "InAppBrowserInternal";

    protected InAppBrowserDriver driver;

    public void init(final InAppBrowserDriver driver) {
        this.driver = driver;

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    InAppBrowserImpl.invokeOn(driver.inAppWebView.getView(), "addJavascriptInterface", new Class[]{Object.class, String.class}, new Object[]{driver.getJavascriptInterface(), "__inappbrowser"});
                } catch (InAppBrowserImpl.InvokeException e) {
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
            if (driver != null) {
                driver.onPageStarted(url);
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
        if (driver != null) {
            return driver.onReceivedHttpAuthRequest(view, handler, host, realm);
        } else {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object onMessage(String id, Object data) {
        if (driver == null) {
            return null;
        }

        if (id.equals("onPageFinished") && data instanceof String) {
            return driver.onPageFinished((String) data);
        } else if (id.equals("onReceivedError") && data instanceof JSONObject) {
            try {
                JSONObject jsonObject = (JSONObject)data;
                return driver.onReceivedError(jsonObject.getInt("errorCode"), jsonObject.getString("description"), jsonObject.getString("url"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (id.equals("exit")) {
            return driver.onExit();
        }

        return null;
    }
}
