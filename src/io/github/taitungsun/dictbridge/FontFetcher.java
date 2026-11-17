package io.github.taitungsun.dictbridge;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Downloads the book-style Chinese font (Noto Serif TC, SIL Open Font License) once, into the
 * app's files folder. Pinned to one commit of the Noto CJK project and checked by SHA-256.
 */
final class FontFetcher {
    private FontFetcher() { }

    static final String NAME = "NotoSerifTC-Regular.otf";
    private static final String URL_ = "https://raw.githubusercontent.com/notofonts/noto-cjk/"
            + "f8d157532fbfaeda587e826d4cd5b21a49186f7c/Serif/SubsetOTF/TC/" + NAME;
    private static final String SHA256 =
            "63515eb0622d589a2f4062e8757c598b3da3bffd053f69abd181c210158ef10c";
    private static volatile boolean running;

    static File file(Context c) { return new File(c.getExternalFilesDir(null), NAME); }

    /** Starts the download in the background if the font is missing; calls done(true) when ready. */
    static void ensure(Context c, Runnable done) {
        File f = file(c);
        if (f.isFile() || running) return;
        running = true;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            File tmp = new File(f.getPath() + ".part");
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(URL_).openConnection();
                h.setConnectTimeout(20000);
                h.setReadTimeout(60000);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) { out.write(buf, 0, n); md.update(buf, 0, n); }
                } finally {
                    h.disconnect();
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest()) hex.append(String.format("%02x", b));
                if (!SHA256.contentEquals(hex) || !tmp.renameTo(f)) {
                    Log.w("DictBridge", "font download rejected");
                    tmp.delete();
                } else {
                    Log.i("DictBridge", "font downloaded");
                    if (done != null) done.run();
                }
            } catch (Exception e) {
                Log.w("DictBridge", "font download failed: " + e);
                tmp.delete();
            } finally {
                running = false;
            }
        }).start();
    }
}
