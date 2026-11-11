package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

/**
 * ACTION_DEFINE (EXTRA_TEXT) opens the Onyx dictionary's PROCESS_TEXT popup.
 * ACTION_TRANSLATE opens the full dictionary app on the word (no popup).
 */
public class BridgeActivity extends Activity {
    static final String DICT = "com.onyx.dict";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null) text = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (text != null) {
            String word = text.toString().trim();
            if (!(Intent.ACTION_TRANSLATE.equals(in.getAction()) && openFullApp(word))) {
                openPopup(word);
            }
        }
        finish();
    }

    /** DictMainActivity reads the word from the "android.intent.action.SEARCH" extra. */
    private boolean openFullApp(String word) {
        Intent out = new Intent("com.onyx.intent.ACTION_DICT")
                .setComponent(new ComponentName(DICT, DICT + ".main.ui.DictMainActivity"))
                .putExtra(Intent.ACTION_SEARCH, word)
                // A fresh task, or a running instance keeps its old screen and ignores the word.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(out);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void openPopup(String word) {
        Intent out = new Intent(Intent.ACTION_PROCESS_TEXT)
                .setComponent(new ComponentName(DICT,
                        DICT + ".translation.ui.ExplanationProcessTextActivity"))
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, word)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        try {
            startActivity(out);
        } catch (RuntimeException e) {
            // Onyx dict missing: fall back to any PROCESS_TEXT handler
            out.setComponent(null);
            try { startActivity(out); } catch (RuntimeException ignored) { }
        }
    }
}
