package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Launcher entry. It exists so Boox lists the app in its freeze settings. */
public class StatusActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Dictionary bridge is active.\n\n"
                + "To keep it working after a reboot, turn off auto-freeze for "
                + "\"Dict Bridge\" in the Boox app settings.");
        tv.setTextSize(20);
        tv.setPadding(48, 48, 48, 48);
        setContentView(tv);
    }
}
