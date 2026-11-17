package io.github.taitungsun.dictbridge;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text on screen of the Economist app, only at the moment Translate is tapped
 * (TranslateEntry calls refresh()). TranslateActivity then widens the selected words to
 * the whole paragraph(s) they belong to. It does not watch the screen otherwise.
 */
public class ParagraphService extends AccessibilityService {
    /** One text block on screen; selStart/selEnd >= 0 when it holds the active selection. */
    static final class Block {
        final String text; final int selStart, selEnd;
        Block(String text, int selStart, int selEnd) { this.text = text; this.selStart = selStart; this.selEnd = selEnd; }
    }

    private static volatile List<Block> visible = new ArrayList<>();
    private static volatile long stamp;
    private static volatile ParagraphService instance;
    private static final String ECONOMIST = "com.economist.lamarr";

    @Override
    protected void onServiceConnected() { instance = this; }

    @Override
    public void onDestroy() { instance = null; super.onDestroy(); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) { /* not used: reads only on Translate */ }

    @Override
    public void onInterrupt() { }

    static boolean connected() { return instance != null; }

    /**
     * Snapshot the Economist window under our invisible TranslateEntry window. Text in any
     * other app is never read.
     */
    static void refresh() {
        ParagraphService s = instance;
        if (s == null) return;
        try {
            for (AccessibilityWindowInfo w : s.getWindows()) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null
                        || !ECONOMIST.contentEquals(r.getPackageName())) continue;
                List<Block> out = new ArrayList<>();
                collect(r, out);
                visible = out;
                stamp = System.currentTimeMillis();
                android.util.Log.i("DictBridge", "read " + out.size() + " blocks from " + r.getPackageName());
                return;
            }
        } catch (RuntimeException ignored) { }
    }

    private static void collect(AccessibilityNodeInfo n, List<Block> out) {
        if (n == null) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0 && n.isVisibleToUser()) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (!r.isEmpty()) out.add(new Block(t.toString(), n.getTextSelectionStart(), n.getTextSelectionEnd()));
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out);
    }

    /** Where a selection sits among the blocks on screen: paras[from..to] inclusive. */
    static final class Range {
        final List<String> paras; final int from, to;
        Range(List<String> paras, int from, int to) { this.paras = paras; this.from = from; this.to = to; }
        List<String> selected() { return new ArrayList<>(paras.subList(from, to + 1)); }
    }

    /** The selection widened to the whole paragraph(s), or null if they can't be found. */
    static Range widen(String selection) {
        if (System.currentTimeMillis() - stamp > 60 * 1000) return null;
        String sel = norm(selection);
        if (sel.isEmpty()) return null;
        List<Block> blocks = visible;
        List<String> paras = new ArrayList<>();
        for (Block b : blocks) paras.add(b.text);

        // 1. The block that holds the active selection (exact, even for a common word)
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.selStart >= 0 && b.selEnd > b.selStart && b.selEnd <= b.text.length()
                    && norm(b.text.substring(b.selStart, b.selEnd)).equals(sel)) {
                return new Range(paras, i, i);
            }
        }
        // 2. The shortest block containing the selection (skips containers holding everything)
        int best = -1;
        for (int i = 0; i < paras.size(); i++) {
            if (norm(paras.get(i)).contains(sel) && (best < 0 || paras.get(i).length() < paras.get(best).length())) best = i;
        }
        if (best >= 0) return new Range(paras, best, best);
        // 3. Selection across paragraphs: from the block holding its start to the one holding its end
        String head = sel.substring(0, Math.min(40, sel.length()));
        String tail = sel.substring(Math.max(0, sel.length() - 40));
        int a = -1, b = -1;
        for (int i = 0; i < paras.size(); i++) {
            String p = norm(paras.get(i));
            if (a < 0 && p.contains(head)) a = i;
            if (a >= 0 && p.contains(tail)) { b = i; break; }
        }
        if (a < 0 || b < 0) return null;
        return new Range(paras, a, b);
    }

    private static String norm(String s) { return s.replaceAll("\\s+", " ").trim(); }
}
