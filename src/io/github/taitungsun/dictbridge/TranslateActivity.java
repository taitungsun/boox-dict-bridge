package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import java.io.File;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ACTION_TRANSLATE: original paragraph(s) on top, Gemini's Taiwan Traditional Chinese
 * translation below. Translations are cached; text size is adjustable. No spinner (e-ink).
 */
public class TranslateActivity extends Activity {
    static final String PREFS = "gemini";
    static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final int BATCH_PARAS = 12, BATCH_CHARS = 2500, CACHE_MAX = 800;
    private static final String WAITING = "翻譯中…", FAILED = "翻譯失敗，點這裡再試一次";
    private static final int EN_SP = 18, ZH_SP = 22, GRAY = Color.rgb(0x77, 0x77, 0x77),
            INK = Color.rgb(0x33, 0x33, 0x33), LINE = Color.rgb(0xBB, 0xBB, 0xBB);

    private LinearLayout en, list;          // top: original, bottom: translation
    private ScrollView enScroll, zhScroll;
    private final List<TextView> enViews = new ArrayList<>(), zhViews = new ArrayList<>();
    private final List<String> paras = new ArrayList<>();
    private String key, model;
    private float scale = 1f;
    private Typeface zhFace = Typeface.DEFAULT;
    private final List<String> zhText = new ArrayList<>(); // null = status message, not body text
    private int generation; // bumps on every new text, so late replies for old text are dropped

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scale = getSharedPreferences(PREFS, MODE_PRIVATE).getFloat("scale", 1f);
        // Book-style Chinese serif (Boox has no Ming/Song font): downloaded once, sans until then
        File f = FontFetcher.file(this);
        try { if (f.isFile()) zhFace = Typeface.createFromFile(f); } catch (RuntimeException ignored) { }
        FontFetcher.ensure(this, () -> runOnUiThread(() -> {
            try { zhFace = Typeface.createFromFile(FontFetcher.file(this)); } catch (RuntimeException e) { return; }
            for (int i = 0; i < zhViews.size(); i++) if (zhText.get(i) != null) setZh(i, zhText.get(i));
        }));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);

        // Header: back (the Page has no Back key) and text size
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(2), dp(20), dp(2));
        TextView back = label("‹  返回文章", 18, Color.BLACK);
        back.setPadding(dp(16), dp(14), dp(16), dp(14));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(sizeButton("A−", -0.1f));
        bar.addView(sizeButton("A+", +0.1f));
        page.addView(bar);
        page.addView(rule(0));

        enScroll = new ScrollView(this);
        en = column();
        enScroll.addView(en);
        page.addView(label2("原文", dp(18)));
        page.addView(enScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        page.addView(divider());
        page.addView(label2("中文翻譯", 0));
        zhScroll = new ScrollView(this);
        list = column();
        zhScroll.addView(list);
        page.addView(zhScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        // The original pane takes what it needs, up to 40% of the screen; the translation gets the rest
        page.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitTop(b - t));

        setContentView(page);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    private void show(Intent in) {
        generation++;
        list.removeAllViews();
        en.removeAllViews();
        enViews.clear();
        zhViews.clear();
        zhText.clear();
        paras.clear();
        CharSequence text = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null) text = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String sel = text == null ? "" : text.toString();
        // Widen the selected words to the whole paragraph(s) when the accessibility service is on
        ParagraphService.Range r = ParagraphService.widen(sel);
        List<String> first = new ArrayList<>();
        if (r != null) for (String w : r.selected()) first.addAll(split(w));
        if (first.isEmpty()) first = split(sel);
        Log.i("DictBridge", "selection " + sel.length() + " chars -> " + first.size()
                + " paragraph(s)" + (r != null ? " (widened)" : ""));
        if (first.isEmpty()) { finish(); return; }

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        key = p.getString("key", "").trim();
        model = DEFAULT_MODEL; // fixed; busy errors fall back to FALLBACK_MODEL
        if (key.isEmpty()) {
            for (String s0 : first) en.addView(enPara(s0));
            TextView t = zhPara("還沒有設定 Gemini API 金鑰。請點這裡開啟「Dict Bridge」填入金鑰。");
            t.setOnClickListener(v -> startActivity(new Intent(this, StatusActivity.class)));
            list.addView(t);
            return;
        }
        add(first);
    }

    /** Append paragraphs to both panes and translate them (cached ones show at once). */
    private void add(List<String> ps) {
        int start = paras.size();
        for (String s0 : ps) {
            paras.add(s0);
            TextView e = enPara(s0), z = zhPara(WAITING);
            zhText.add(null);
            enViews.add(e);
            zhViews.add(z);
            en.addView(e);
            list.addView(z);
            link(e, z, zhScroll);
            link(z, e, enScroll);
        }
        fitTop(0);
        // Fill from the cache, translate the rest in batches
        List<Integer> todo = new ArrayList<>();
        for (int i = start; i < paras.size(); i++) {
            String hit = cacheGet(paras.get(i));
            if (hit != null) setZh(i, hit); else todo.add(i);
        }
        int k = 0;
        while (k < todo.size()) {
            int end = k, chars = 0;
            while (end < todo.size() && end - k < BATCH_PARAS
                    && (end == k || chars + paras.get(todo.get(end)).length() <= BATCH_CHARS)) {
                chars += paras.get(todo.get(end)).length();
                end++;
            }
            run(new ArrayList<>(todo.subList(k, end)), generation);
            k = end;
        }
    }

    /** Translate the paragraphs at the given indexes on a background thread. */
    private void run(List<Integer> idx, int gen) {
        for (int i : idx) { setZh(i, null); zhViews.get(i).setOnClickListener(null); }
        List<String> part = new ArrayList<>();
        for (int i : idx) part.add(paras.get(i));
        new Thread(() -> {
            List<String> out;
            try { out = gemini(part, key, model); } catch (Exception e) { out = null; }
            // A wrong paragraph count: fall back to one request per paragraph
            if ((out == null || out.size() != part.size()) && part.size() > 1) {
                runOnUiThread(() -> {
                    if (gen != generation) return;
                    for (int i : idx) { List<Integer> one = new ArrayList<>(); one.add(i); run(one, gen); }
                });
                return;
            }
            final List<String> res = out;
            final int code = lastCode;
            if (res != null) for (int j = 0; j < part.size(); j++) cachePut(part.get(j), res.get(j));
            runOnUiThread(() -> {
                if (gen != generation) return;
                for (int j = 0; j < idx.size(); j++) {
                    int i = idx.get(j);
                    TextView z = zhViews.get(i);
                    if (res != null) {
                        setZh(i, res.get(j));
                        link(z, enViews.get(i), enScroll);
                    } else {
                        zhText.set(i, null);
                        z.setTypeface(Typeface.DEFAULT);
                        z.setTextColor(GRAY);
                        z.setText(FAILED + "\n（" + reason(code) + "）");
                        List<Integer> one = new ArrayList<>(); one.add(i);
                        z.setOnClickListener(v -> run(one, gen));
                    }
                }
            });
        }).start();
    }

    // ---- translation cache (SharedPreferences "cache", key = SHA-1 of the paragraph) ----

    private String cacheGet(String para) {
        return getSharedPreferences("cache", MODE_PRIVATE).getString(hash(para), null);
    }

    private void cachePut(String para, String zh) {
        SharedPreferences c = getSharedPreferences("cache", MODE_PRIVATE);
        SharedPreferences.Editor ed = c.edit();
        if (c.getAll().size() >= CACHE_MAX) ed.clear(); // crude cap: start over
        ed.putString(hash(para), zh).apply();
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1")
                    .digest(s.replaceAll("\\s+", " ").trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    // ---- views ----

    private TextView sizeButton(String label, float step) {
        TextView t = label(label, 16, Color.BLACK);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setPadding(dp(8), dp(6), dp(8), dp(6));
        t.setBackground(box());
        t.setOnClickListener(v -> {
            scale = Math.max(0.8f, Math.min(1.6f, Math.round((scale + step) * 10) / 10f));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat("scale", scale).apply();
            for (TextView e : enViews) { e.setTextSize(EN_SP * scale); indent(e, e.getText().toString(), 1.5f); }
            for (int i = 0; i < zhViews.size(); i++) {
                zhViews.get(i).setTextSize(ZH_SP * scale);
                if (zhText.get(i) != null) setZh(i, zhText.get(i));
            }
            fitTop(0);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private android.graphics.drawable.GradientDrawable box() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(1), Color.BLACK);
        g.setCornerRadius(dp(6));
        return g;
    }

    private int pageHeight;

    private void fitTop(int h) {
        if (h > 0) pageHeight = h;
        if (pageHeight == 0) return;
        en.measure(View.MeasureSpec.makeMeasureSpec(enScroll.getWidth() > 0 ? enScroll.getWidth()
                        : getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int want = Math.min(en.getMeasuredHeight(), (int) (pageHeight * 0.4f));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) enScroll.getLayoutParams();
        if (lp.height != want) {
            lp.height = want;
            enScroll.post(() -> enScroll.setLayoutParams(lp));
        }
    }

    /** A short centered line between the original and the translation, like a section break in a book. */
    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(GRAY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(1));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(4);
        v.setLayoutParams(lp);
        return v;
    }

    /** Small gray pane label, aligned with the text column. */
    private TextView label2(String name, int top) {
        TextView t = label(name, 13, GRAY);
        t.setLetterSpacing(0.2f);
        t.setPadding(dp(40), top, dp(40), 0);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(40), dp(8), dp(40), dp(24));
        return l;
    }

    /** A thin line; inset > 0 indents it from both sides. */
    private View rule(int inset) {
        View v = new View(this);
        v.setBackgroundColor(inset > 0 ? LINE : Color.BLACK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = lp.rightMargin = inset;
        v.setLayoutParams(lp);
        return v;
    }

    /** Tapping a paragraph in one pane scrolls the other pane to its partner. */
    private void link(View from, View to, ScrollView target) {
        from.setOnClickListener(v -> {
            if (paras.size() > 1) target.smoothScrollTo(0, Math.max(0, to.getTop() - dp(4)));
        });
    }

    private TextView enPara(String s0) {
        TextView t = label("", 0, INK);
        t.setTextSize(EN_SP * scale);
        t.setTypeface(Typeface.SERIF);
        t.setLineSpacing(0, 1.5f);
        t.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        t.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        t.setPadding(0, 0, 0, dp(6));
        indent(t, s0, 1.5f);
        return t;
    }

    private TextView zhPara(String s0) {
        TextView t = label(s0, 0, GRAY);
        t.setTextSize(ZH_SP * scale);
        t.setLineSpacing(0, 1.35f);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    /** Body text of the translation: book serif, first line indented by two characters. */
    private void setZh(int i, String text) {
        TextView z = zhViews.get(i);
        zhText.set(i, text);
        if (text == null) {
            z.setTypeface(Typeface.DEFAULT);
            z.setTextColor(GRAY);
            z.setText(WAITING);
            return;
        }
        z.setTypeface(zhFace);
        z.setTextColor(Color.BLACK);
        indent(z, text, 2f);
    }

    /** First-line indent of {@code ems} times the text size. */
    private static void indent(TextView t, String text, float ems) {
        SpannableString sp = new SpannableString(text);
        sp.setSpan(new LeadingMarginSpan.Standard(Math.round(t.getTextSize() * ems), 0),
                0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        t.setText(sp);
    }

    private volatile String lastError;

    static final String FALLBACK_MODEL = "gemini-3.8-flash";
    private volatile int lastCode;

    /** Retries busy/rate-limit errors (429/500/503), then tries the lighter model once more. */
    private List<String> gemini(List<String> paras, String key, String model) throws Exception {
        String[] models = model.equals(FALLBACK_MODEL)
                ? new String[] {model} : new String[] {model, FALLBACK_MODEL};
        long[] waits = {0, 3000, 8000};
        for (String m : models) {
            for (long w : waits) {
                if (w > 0) Thread.sleep(w);
                lastCode = 0;
                List<String> out;
                try { out = call(paras, key, m); } catch (Exception e) { out = null; }
                if (out != null) return out;
                // Busy (429/500/503) or no network yet (0, e.g. Wi-Fi waking up): wait and retry.
                // Other errors (key, billing, bad request) won't get better by retrying.
                if (lastCode != 0 && lastCode != 429 && lastCode != 500 && lastCode != 503) return null;
            }
        }
        return null;
    }

    private List<String> call(List<String> paras, String key, String model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", Prompt.SYSTEM))));
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String user = "今天的日期：" + today + "\n\n以下 JSON 陣列共有 " + paras.size()
                + " 段，請逐段翻譯：\n" + new JSONArray(paras).toString(2);
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", user)))));
        body.put("generationConfig", new JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", new JSONObject().put("type", "ARRAY")
                        .put("items", new JSONObject().put("type", "STRING"))));

        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, "UTF-8") + ":generateContent");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(20000);
            // Busy servers sometimes hold the connection; give up on the main model sooner
            c.setReadTimeout(model.equals(FALLBACK_MODEL) ? 120000 : 45000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("x-goog-api-key", key);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            long t0 = System.currentTimeMillis();
            Log.i("DictBridge", "POST " + model + " paras=" + paras.size());
            int code = c.getResponseCode();
            lastCode = code;
            Log.i("DictBridge", "HTTP " + code + " after " + (System.currentTimeMillis() - t0) + " ms");
            String resp = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code != 200) {
                String msg = "HTTP " + code;
                try { msg += " " + new JSONObject(resp).getJSONObject("error").optString("message"); }
                catch (Exception ignored) { }
                Log.w("DictBridge", resp);
                lastError = msg.length() > 160 ? msg.substring(0, 160) + "…" : msg;
                return null;
            }
            String txt = new JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text");
            JSONArray arr = new JSONArray(txt);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i).trim());
            lastError = out.size() == paras.size() ? null : "段落數不符";
            return out;
        } catch (Exception e) {
            Log.w("DictBridge", "request failed", e);
            lastError = e.getClass().getSimpleName();
            throw e;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    /** Paragraphs = blank-line blocks; if there are none, single lines. */
    static List<String> split(String text) {
        String t = text.replace("\r", "").trim();
        String[] parts = t.split("\\n\\s*\\n+");
        if (parts.length == 1) parts = t.split("\\n+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.replaceAll("\\s+", " ").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** A short, plain reason instead of the raw server message. */
    private static String reason(int code) {
        switch (code) {
            case 0: return "網路連不上";
            case 429: case 500: case 503: return "Gemini 目前忙線中";
            case 402: return "API 額度已用完";
            case 400: case 401: case 403: return "API 金鑰有問題";
            default: return "錯誤代碼 " + code;
        }
    }

    private TextView label(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        if (sp > 0) t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
