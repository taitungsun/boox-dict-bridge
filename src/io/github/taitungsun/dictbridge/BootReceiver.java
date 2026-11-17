package io.github.taitungsun.dictbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot, switches the paragraph service back on if Boox dropped it. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        TranslateEntry.ensure(c);
    }
}
