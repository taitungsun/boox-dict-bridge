package io.github.taitungsun.dictbridge;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Launcher entry and settings. Boox only lists launcher apps in its freeze settings, so the
 * icon also keeps the app from being frozen. Shows what works, takes the Gemini API key.
 */
public class StatusActivity extends Activity {
    private static final int GRAY = Color.rgb(0x66, 0x66, 0x66);
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build(); // refresh: the accessibility switch may have changed in Settings
    }

    private void build() {
        TranslateEntry.ensure(this);
        SharedPreferences p = getSharedPreferences(TranslateActivity.PREFS, MODE_PRIVATE);
        boolean a11y = TranslateEntry.enabled(this);
        boolean hasKey = !p.getString("key", "").trim().isEmpty();

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(32), dp(36), dp(32), dp(40));

        // 字典橋 large, "Dict Bridge" small beside it on the same baseline
        android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder("字典橋  Dict Bridge");
        t.setSpan(new android.text.style.RelativeSizeSpan(0.55f), 3, t.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setSpan(new android.text.style.ForegroundColorSpan(GRAY), 3, t.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView title = text("", 32, Color.BLACK);
        title.setText(t);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title);
        TextView sub = text("經濟學人單字解析與段落翻譯補完計畫", 16, GRAY);
        sub.setPadding(0, dp(4), 0, dp(24));
        page.addView(sub);

        // Status
        LinearLayout st = card("目前狀態");
        st.addView(row("整段翻譯", a11y ? "已開啟" : "未開啟", a11y));
        st.addView(row("Gemini 金鑰", hasKey ? "已設定" : "未設定", hasKey));
        if (!a11y) {
            st.addView(hint("請到「協助工具」開啟「Dict Bridge paragraphs」，翻譯時才會翻整段，而不是只翻選到的字。"));
            st.addView(button("開啟協助工具設定",
                    v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        }

        // API key
        LinearLayout k = card("Gemini API 金鑰");
        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(p.getString("key", ""));
        key.setHint("貼上金鑰");
        key.setTextSize(18);
        key.setBackground(box(1));
        key.setPadding(dp(14), dp(12), dp(14), dp(12));
        k.addView(key, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView saved = hint("已儲存。");
        saved.setVisibility(View.GONE);
        k.addView(button("儲存", v -> {
            p.edit().putString("key", key.getText().toString().trim()).remove("model").apply();
            saved.setVisibility(View.VISIBLE);
        }));
        k.addView(saved);

        // Keep it running
        LinearLayout f = card("重開機後保持運作");
        f.addView(hint("Boox 會凍結新安裝的 app。請到「應用」分頁，點上方的雪花圖示，"
                + "關閉「Dictionary」的凍結。清單裡顯示的名稱是「Dictionary」，不是「Dict Bridge」。"));

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(Color.WHITE);
        s.addView(page);
        setContentView(s);
    }

    private LinearLayout card(String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(2));
        c.setPadding(dp(20), dp(16), dp(20), dp(18));
        TextView h = text(heading, 18, Color.BLACK);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, dp(10));
        c.addView(h);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(18);
        page.addView(c, lp);
        return c;
    }

    /** "label ........ value" with a filled or hollow dot for good / needs action. */
    private View row(String name, String value, boolean ok) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        r.addView(text(name, 17, Color.BLACK),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text((ok ? "●  " : "○  ") + value, 17, ok ? Color.BLACK : GRAY);
        if (!ok) v.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(v);
        return r;
    }

    private TextView hint(String s) {
        TextView t = text(s, 16, GRAY);
        t.setLineSpacing(0, 1.3f);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private TextView button(String label, View.OnClickListener l) {
        TextView b = text(label, 17, Color.BLACK);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(box(2));
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable box(int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(stroke), Color.BLACK);
        g.setCornerRadius(dp(10));
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
