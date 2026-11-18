package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;

/**
 * Receives Economist's ACTION_TRANSLATE in an invisible, unfocusable window, so the article
 * stays readable to ParagraphService. If Boox switched the service off (the NaviBall toggle
 * rewrites the accessibility list), turns it back on, waits for it, then opens TranslateActivity.
 */
public class TranslateEntry extends Activity {
    private static final long WAIT_MS = 8000, STEP_MS = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        if (ParagraphService.connected()) {
            if (!getIntent().getBooleanExtra(AGAIN, false)) {
                // Running already: usually reads at once. The first read after the service
                // started can come back empty (seen on the Nova3): retry for a second, then
                // step aside like a fresh start. Text from any other app goes straight through.
                android.net.Uri ref = getReferrer();
                boolean economist = ref != null && "com.economist.lamarr".equals(ref.getHost());
                if (!economist) { ParagraphService.refresh(); forward(); return; }
                long soon = System.currentTimeMillis() + 1000;
                new Thread(() -> {
                    boolean ok;
                    while (!(ok = ParagraphService.refresh()) && System.currentTimeMillis() < soon) nap();
                    boolean read = ok;
                    runOnUiThread(() -> {
                        if (!read) {
                            Log.i("DictBridge", "article not readable yet; stepping aside");
                            getIntent().putExtra(REREAD, true);
                        }
                        forward();
                    });
                }).start();
                return;
            }
            // Second pass after a fresh start: the window list may need a moment
            long until = System.currentTimeMillis() + 3000;
            new Thread(() -> {
                while (!ParagraphService.refresh() && System.currentTimeMillis() < until) nap();
                runOnUiThread(this::forward);
            }).start();
            return;
        }
        boolean turnedOn = ensure(this);
        Log.i("DictBridge", "service not connected; re-enabled=" + turnedOn);
        // Enabled but not bound yet (e.g. right after an update): wait for it as well
        if (!turnedOn && !enabled(this)) { forward(); return; }
        Log.i("DictBridge", "waiting for the article to be readable");
        // A just-bound service can't read the article yet: Android hands over a window's
        // contents only after that window has had focus since the bind. So wait for the bind,
        // then let TranslateActivity step aside (REREAD) so Economist gets focus; it comes back
        // through here with AGAIN, which reads the article as usual.
        long until = System.currentTimeMillis() + WAIT_MS;
        new Thread(() -> {
            while (!ParagraphService.connected() && System.currentTimeMillis() < until) nap();
            runOnUiThread(() -> {
                if (ParagraphService.connected()) getIntent().putExtra(REREAD, true);
                forward();
            });
        }).start();
    }

    static final String REREAD = "io.github.taitungsun.dictbridge.REREAD";
    static final String AGAIN = "io.github.taitungsun.dictbridge.AGAIN";

    private static void nap() {
        try { Thread.sleep(STEP_MS); } catch (InterruptedException ignored) { }
    }

    private boolean done;

    private void forward() {
        if (done) return;
        done = true;
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
