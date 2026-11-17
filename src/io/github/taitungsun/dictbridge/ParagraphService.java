package io.github.taitungsun.dictbridge;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Whenever the text selection changes in The Economist, remembers the paragraphs on screen.
 * TranslateActivity then widens the selected words to the whole paragraph(s) they belong to.
 */
public class ParagraphService extends AccessibilityService {
    private static volatile List<String> visible = new ArrayList<>();
    private static volatile long stamp;

    private static volatile ParagraphService instance;

    @Override
    protected void onServiceConnected() { instance = this; }

    @Override
    public void onDestroy() { instance = null; super.onDestroy(); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        snapshot(getRootInActiveWindow());
    }

    static boolean connected() { return instance != null; }

    /** Snapshot the Economist window, even when another (translucent) window has focus. */
    static void refresh() {
        ParagraphService s = instance;
        if (s == null) return;
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : s.getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null
                        && "com.economist.lamarr".contentEquals(r.getPackageName())) {
                    s.snapshot(r);
                    return;
                }
            }
        } catch (RuntimeException ignored) { }
        s.snapshot(s.getRootInActiveWindow());
    }

    private void snapshot(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null
                || !"com.economist.lamarr".contentEquals(root.getPackageName())) return;
        List<String> out = new ArrayList<>();
        collect(root, out);
        if (out.isEmpty()) return;
        visible = out;
        stamp = System.currentTimeMillis();
    }

    private static void collect(AccessibilityNodeInfo n, List<String> out) {
        if (n == null) return;
        CharSequence t = n.getText();
        if (t != null && n.isVisibleToUser()) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            if (!r.isEmpty()) out.add(t.toString());
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out);
    }

    @Override
    public void onInterrupt() { }

    /** Where a selection sits among the paragraphs on screen: paras[from..to] inclusive. */
    static final class Range {
        final List<String> paras; final int from, to;
        Range(List<String> paras, int from, int to) { this.paras = paras; this.from = from; this.to = to; }
        List<String> selected() { return new ArrayList<>(paras.subList(from, to + 1)); }
    }

    /** The selection widened to the whole paragraph(s), or null if they can't be found. */
    static Range widen(String selection) {
        if (System.currentTimeMillis() - stamp > 10 * 60 * 1000) return null;
        String sel = norm(selection);
        if (sel.isEmpty()) return null;
        List<String> paras = visible;
        // Selection inside one paragraph
        for (int i = 0; i < paras.size(); i++) if (norm(paras.get(i)).contains(sel)) return new Range(paras, i, i);
        // Selection across paragraphs: from the one holding its start to the one holding its end
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
