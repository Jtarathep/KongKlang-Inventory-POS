package com.kongklang.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private WebView web;
    private Uri server;
    private PermissionRequest cameraRequest;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private LinearLayout layout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.view.Window window = getWindow();
        window.setStatusBarColor(0xff173b75);
        window.setNavigationBarColor(0xff173b75);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        Button settings = new Button(this);
        settings.setText("KONGKLANG  ·  ตั้งค่าเว็บร้าน");
        settings.setTextColor(0xff173b75);
        settings.setBackgroundColor(0xffedf3ff);
        settings.setOnClickListener(view -> selectServer());
        layout.addView(settings);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        layout.addView(progress, new LinearLayout.LayoutParams(-1, 6));
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(android.view.Gravity.CENTER);
        errorPanel.setPadding(32, 48, 32, 48);
        TextView message = new TextView(this);
        message.setText("เชื่อมต่อร้านไม่ได้\nตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง");
        message.setGravity(android.view.Gravity.CENTER);
        message.setTextSize(18);
        message.setTextColor(0xff173b75);
        errorPanel.addView(message);
        Button retry = new Button(this);
        retry.setText("ลองเชื่อมต่อใหม่");
        retry.setOnClickListener(v -> { if (server != null) web.loadUrl(server + "/mobile"); else selectServer(); });
        errorPanel.addView(retry);
        errorPanel.setVisibility(View.GONE);
        layout.addView(errorPanel, new LinearLayout.LayoutParams(-1, 0, 1));
        web = new WebView(this);
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (trusted(target) && "/login".equals(target.getPath()) && target.getQueryParameter("mobile") == null) {
                    view.loadUrl(target.buildUpon().appendQueryParameter("mobile", "1").build().toString());
                    return true;
                }
                return !trusted(target);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                errorPanel.setVisibility(View.GONE);
                web.setVisibility(View.VISIBLE);
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    web.setVisibility(View.GONE);
                    errorPanel.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                Uri page = Uri.parse(url);
                if (trusted(page) && ("/".equals(page.getPath()) || "".equals(page.getPath()))) {
                    view.loadUrl(server + "/mobile");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
            @Override public void onPermissionRequest(PermissionRequest request) {
                if (!trusted(request.getOrigin()) || !java.util.Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    if (cameraRequest != null) cameraRequest.deny();
                    cameraRequest = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (cameraRequest == request) cameraRequest = null;
            }
        });
        String saved = getPreferences(MODE_PRIVATE).getString("server", "");
        if (saved.isEmpty() || !connect(saved)) selectServer();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else new AlertDialog.Builder(this).setTitle("ออกจากแอปคงคลัง?")
            .setNegativeButton("อยู่ต่อ", null).setPositiveButton("ออก", (dialog, which) -> finish()).show();
    }

    @Override protected void onDestroy() {
        if (cameraRequest != null) { cameraRequest.deny(); cameraRequest = null; }
        if (web != null) { layout.removeView(web); web.destroy(); web = null; }
        super.onDestroy();
    }

    private boolean trusted(Uri url) {
        return server != null && "https".equalsIgnoreCase(url.getScheme())
                && server.getHost().equalsIgnoreCase(url.getHost()) && port(server) == port(url);
    }

    private int port(Uri url) { return url.getPort() == -1 ? 443 : url.getPort(); }

    private void selectServer() {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setSingleLine(true);
        field.setHint("https://เว็บคงคลังของร้าน");
        if (server != null) field.setText(server.toString());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("เชื่อมต่อเว็บคงคลัง")
                .setMessage("กรอก URL แบบ HTTPS จากผู้ดูแลร้าน แล้วเข้าสู่ระบบด้วยบัญชีร้านเดิม")
                .setView(field).setNegativeButton("ยกเลิก", null).setPositiveButton("เชื่อมต่อ", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (connect(field.getText().toString().trim())) dialog.dismiss();
            else field.setError("กรอก URL แบบ HTTPS ให้ถูกต้อง");
        }));
        dialog.show();
    }

    private boolean connect(String value) {
        try {
            java.net.URI parsed = new java.net.URI(value);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null || parsed.getUserInfo() != null) return false;
            server = Uri.parse(new java.net.URI("https", null, parsed.getHost(), parsed.getPort(), null, null, null).toString());
        } catch (java.net.URISyntaxException invalid) { return false; }
        getPreferences(MODE_PRIVATE).edit().putString("server", server.toString()).apply();
        web.clearHistory();
        web.loadUrl(server + "/mobile");
        return true;
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 1 || cameraRequest == null) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && trusted(cameraRequest.getOrigin())) {
            cameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else cameraRequest.deny();
        cameraRequest = null;
    }
}
