package com.priscillabuddy.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceResponse;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private static final String KEY_ALIAS = "buddy_local_data_key_v1";
    private WebView web;
    private SharedPreferences prefs;
    private WebViewAssetLoader assets;

    private boolean allowed(Uri u) {
        return u != null && "https".equals(u.getScheme())
            && "appassets.androidplatform.net".equals(u.getHost())
            && (u.getPort() == -1 || u.getPort() == 443)
            && u.getUserInfo() == null
            && u.getPath() != null && u.getPath().startsWith("/assets/");
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return gen.generateKey();
        }
        return ((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private synchronized String decryptState() {
        String saved = prefs.getString("state", null);
        if (saved == null) return "";
        try {
            byte[] b = Base64.decode(saved, Base64.NO_WRAP);
            if (b.length < 29) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(b, 0, 12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(b, 12, b.length)), StandardCharsets.UTF_8);
        } catch (Exception ex) { return ""; }
    }

    private synchronized boolean encryptState(String value) {
        if (value == null || value.length() > 2000000) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] enc = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] data = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, data, 0, iv.length);
            System.arraycopy(enc, 0, data, iv.length, enc.length);
            return prefs.edit().putString("state", Base64.encodeToString(data, Base64.NO_WRAP)).commit();
        } catch (Exception ex) { return false; }
    }

    private String hashPin(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 160000, 256);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return Base64.encodeToString(hash, Base64.NO_WRAP);
    }
    private boolean validPin(String p) { return p != null && p.matches("[0-9]{6,12}"); }

    private synchronized boolean verify(String pin) {
        if (!prefs.contains("pin_hash")) return false;
        long lockedUntil = prefs.getLong("lock_until", 0);
        if (System.currentTimeMillis() < lockedUntil) return false;
        try {
            byte[] salt = Base64.decode(prefs.getString("pin_salt", ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(prefs.getString("pin_hash", ""), Base64.NO_WRAP);
            byte[] entered = Base64.decode(hashPin(pin, salt), Base64.NO_WRAP);
            boolean ok = java.security.MessageDigest.isEqual(expected, entered);
            if (ok) prefs.edit().putInt("failures", 0).remove("lock_until").apply();
            else {
                int failures = prefs.getInt("failures", 0) + 1;
                SharedPreferences.Editor ed = prefs.edit().putInt("failures", failures);
                if (failures >= 5) ed.putLong("lock_until", System.currentTimeMillis() + 300000);
                ed.apply();
            }
            return ok;
        } catch (Exception ex) { return false; }
    }

    private synchronized boolean setPin(String old, String next) {
        if (!validPin(next)) return false;
        if (prefs.contains("pin_hash") && !verify(old)) return false;
        try {
            byte[] salt = new byte[24];
            new SecureRandom().nextBytes(salt);
            return prefs.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("pin_hash", hashPin(next, salt)).putInt("failures", 0).remove("lock_until").commit();
        } catch (Exception ex) { return false; }
    }

    public class LocalApi {
        @JavascriptInterface public String read() { return decryptState(); }
        @JavascriptInterface public boolean save(String json) { return encryptState(json); }
        @JavascriptInterface public boolean hasPin() { return prefs.contains("pin_hash"); }
        @JavascriptInterface public boolean setPin(String old, String next) { return MainActivity.this.setPin(old, next); }
        @JavascriptInterface public boolean verifyPin(String pin) { return verify(pin); }
        @JavascriptInterface public long lockedForSeconds() {
            return Math.max(0, (prefs.getLong("lock_until", 0) - System.currentTimeMillis()) / 1000);
        }
        @JavascriptInterface public void exitToHome() {
            runOnUiThread(() -> {
                web.evaluateJavascript("window.__buddyParentUnlocked===true", result -> {
                    if ("true".equals(result)) {
                        IntentHome();
                    }
                });
            });
        }
    }
    private void IntentHome() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_MAIN);
        i.addCategory(android.content.Intent.CATEGORY_HOME);
        i.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("buddy_secure", Context.MODE_PRIVATE);
        fullscreen();
        assets = new WebViewAssetLoader.Builder().addPathHandler("/assets/",
            new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web = new WebView(this);
        setContentView(web);
        web.setBackgroundColor(Color.rgb(246,244,255));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(false); // Data is encrypted in Android private storage instead
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setGeolocationEnabled(false);
        s.setSavePassword(false);
        s.setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(false);
        web.addJavascriptInterface(new LocalApi(), "BuddyNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !allowed(r.getUrl());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                if (!allowed(request.getUrl())) {
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                        java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }
                return assets.shouldInterceptRequest(request.getUrl());
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) { h.cancel(); }
        });
        web.loadUrl(HOME);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(focused) fullscreen();
    }
    @Override public void onBackPressed() {
        // Child UI controls navigation. Android screen pinning (if supported) is configured separately.
    }
    @Override public void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
