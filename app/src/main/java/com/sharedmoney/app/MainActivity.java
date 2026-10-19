package com.sharedmoney.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Window;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_CREATE_BACKUP = 4101;
    private static final int REQUEST_OPEN_BACKUP = 4102;
    private WebView webView;
    private String pendingBackupJson = null;
    private ValueCallback<Uri[]> filePathCallback;

    private class AndroidBridge {
        @JavascriptInterface
        public void saveBackup(final String filename, final String json) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    pendingBackupJson = json;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    String lower = filename == null ? "" : filename.toLowerCase();
                    if (lower.endsWith(".csv")) {
                        intent.setType("text/csv");
                    } else if (lower.endsWith(".json")) {
                        intent.setType("application/json");
                    } else {
                        intent.setType("text/plain");
                    }
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    startActivityForResult(intent, REQUEST_CREATE_BACKUP);
                }
            });
        }

        @JavascriptInterface
        public void openBackupPicker() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "application/json", "text/json", "text/plain",
                        "text/csv", "text/comma-separated-values",
                        "application/csv", "application/vnd.ms-excel"
                    });
                    startActivityForResult(intent, REQUEST_OPEN_BACKUP);
                }
            });
        }

        @JavascriptInterface
        public void printPassbook(final String html) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    final WebView printWebView = new WebView(MainActivity.this);
                    WebSettings printSettings = printWebView.getSettings();
                    printSettings.setJavaScriptEnabled(true);
                    printSettings.setDomStorageEnabled(true);
                    printWebView.setBackgroundColor(Color.WHITE);
                    printWebView.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView view, String url) {
                            PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                            PrintDocumentAdapter adapter = printWebView.createPrintDocumentAdapter("SharedMoney-Passbook");
                            PrintAttributes attributes = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(new PrintAttributes.Resolution("shared_money", "Shared Money", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build();
                            printManager.print("SharedMoney-Passbook", adapter, attributes);
                        }
                    });
                    printWebView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(247, 240, 231));
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(247, 240, 231));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, REQUEST_OPEN_BACKUP);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == REQUEST_CREATE_BACKUP) {
            if (resultCode == RESULT_OK && resultData != null && resultData.getData() != null && pendingBackupJson != null) {
                Uri uri = resultData.getData();
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Could not open destination");
                    out.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    showToastInWeb("Backup exported successfully");
                } catch (Exception e) {
                    showToastInWeb("Export failed. Please try again.");
                }
            } else if (resultCode == RESULT_CANCELED) {
                showToastInWeb("Export cancelled");
            }
            pendingBackupJson = null;
            return;
        }

        if (requestCode == REQUEST_OPEN_BACKUP) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && resultData != null && resultData.getData() != null) {
                    results = new Uri[] { resultData.getData() };
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            if (resultCode == RESULT_OK && resultData != null && resultData.getData() != null) {
                readAndImportBackup(resultData.getData());
            }
        }
    }

    private void readAndImportBackup(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Could not open selected file");
            byte[] bytes = new byte[8192];
            int count;
            while ((count = in.read(bytes)) != -1) buffer.write(bytes, 0, count);
            String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            final String escaped = org.json.JSONObject.quote(json);
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    webView.evaluateJavascript(
                        "if(window.confirm('Import will replace all current data. Continue?')){" +
                        "window.importBackupJSON(" + escaped + ");}",
                        null
                    );
                }
            });
        } catch (Exception e) {
            showToastInWeb("Could not read backup file");
        }
    }

    private void showToastInWeb(final String message) {
        final String escaped = org.json.JSONObject.quote(message);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (webView != null) webView.evaluateJavascript("showToast(" + escaped + ");", null);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
