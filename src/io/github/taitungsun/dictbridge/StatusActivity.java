package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Launcher entry: Boox only lists launcher apps in its freeze settings. Also holds the Gemini settings. */
public class StatusActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences p = getSharedPreferences(TranslateActivity.PREFS, MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 48, 48, 48);

        box.addView(text("Dictionary bridge is active.\n\n"
                + "To keep it working after a reboot, turn off auto-freeze for "
                + "\"Dictionary\" in Apps > Freeze Settings.\n\n"
                + "Translate uses Gemini. API key:"));
        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(p.getString("key", ""));
        box.addView(key);


        Button save = new Button(this);
        save.setText("Save");
        TextView done = text("");
        save.setOnClickListener(v -> {
            p.edit().putString("key", key.getText().toString().trim()).remove("model").apply();
            done.setText("Saved.");
        });
        box.addView(save);
        box.addView(done);

        // Whole-paragraph Translate needs the accessibility service; Boox may switch it off
        TranslateEntry.ensure(this);
        boolean enabled = TranslateEntry.enabled(this);
        box.addView(text("Whole-paragraph Translate: " + (enabled ? "ON" : "OFF")
                + (enabled ? "" : "\nTurn on \"Dict Bridge paragraphs\" in Accessibility.")));
        if (!enabled) {
            Button a11y = new Button(this);
            a11y.setText("Open Accessibility settings");
            a11y.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            box.addView(a11y);
        }

        ScrollView s = new ScrollView(this);
        s.addView(box);
        setContentView(s);
    }

    private TextView text(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(20);
        tv.setPadding(0, 16, 0, 8);
        return tv;
    }
}
