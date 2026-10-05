package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/** Forwards ACTION_DEFINE / ACTION_TRANSLATE (EXTRA_TEXT) to the Onyx dictionary's PROCESS_TEXT popup. */
public class BridgeActivity extends Activity {
    protected String target() {
        return "com.onyx.dict.translation.ui.ExplanationProcessTextActivity";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null) text = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (text != null) {
            Intent out = new Intent(Intent.ACTION_PROCESS_TEXT)
                    .setComponent(new ComponentName("com.onyx.dict", target()))
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, text.toString().trim())
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
            try {
                startActivity(out);
            } catch (RuntimeException e) {
                // Onyx dict missing: fall back to any PROCESS_TEXT handler
                out.setComponent(null);
                try { startActivity(out); } catch (RuntimeException ignored) { }
            }
        }
        finish();
    }
}
