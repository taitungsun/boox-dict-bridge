package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

/**
 * Receives Economist's ACTION_TRANSLATE in an invisible, unfocusable window, so the article
 * stays readable to ParagraphService. If Boox switched the service off (the NaviBall toggle
 * rewrites the accessibility list), turns it back on, waits for it, then opens TranslateActivity.
 */
public class TranslateEntry extends Activity {
    private static final long WAIT_MS = 2500, STEP_MS = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        if (ParagraphService.connected()) {
            forward();
            return;
        }
        boolean turnedOn = ensure(this);
        Log.i("DictBridge", "service was off; re-enabled=" + turnedOn);
        if (!turnedOn) { forward(); return; }
        Handler h = new Handler(Looper.getMainLooper());
        long until = System.currentTimeMillis() + WAIT_MS;
        h.post(new Runnable() {
            @Override public void run() {
                if (ParagraphService.connected() || System.currentTimeMillis() > until) forward();
                else h.postDelayed(this, STEP_MS);
            }
        });
    }

    private boolean done;

    private void forward() {
        if (done) return;
        done = true;
        ParagraphService.refresh();
        Intent out = new Intent(getIntent()).setClass(this, TranslateActivity.class);
        out.setFlags(0);
        startActivity(out);
        finish();
        overridePendingTransition(0, 0);
    }

    static final String SERVICE = "io.github.taitungsun.dictbridge/.ParagraphService";

    static boolean enabled(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && (on.contains(SERVICE)
                || on.contains("io.github.taitungsun.dictbridge/io.github.taitungsun.dictbridge.ParagraphService"));
    }

    /**
     * Adds ParagraphService to the enabled accessibility services. Needs WRITE_SECURE_SETTINGS,
     * granted once over adb. Returns true if it was switched on now.
     */
    static boolean ensure(Context c) {
        if (enabled(c)) return false;
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                != PackageManager.PERMISSION_GRANTED) return false;
        try {
            String on = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String list = (on == null || on.isEmpty() || on.equals("null")) ? SERVICE : on + ":" + SERVICE;
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
