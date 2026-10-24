package com.alex.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Performs only the narrowly defined actions in ActionRegistry. It never collects screen text on its own. */
public class AlexAccessibilityService extends AccessibilityService {
    public static AlexAccessibilityService instance;
    private final Handler h = new Handler(Looper.getMainLooper());

    public static boolean active() { return instance != null; }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(Intent i) { instance = null; return super.onUnbind(i); }

    private AccessibilityNodeInfo clickableParent(AccessibilityNodeInfo n) {
        while (n != null && !n.isClickable()) n = n.getParent();
        return n;
    }

    public boolean clickText(String t, boolean longPress) {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r == null || t == null || t.isEmpty()) return false;
        List<AccessibilityNodeInfo> l = r.findAccessibilityNodeInfosByText(t);
        for (AccessibilityNodeInfo n : l) {
            AccessibilityNodeInfo c = clickableParent(n);
            if (c != null && c.performAction(longPress ? AccessibilityNodeInfo.ACTION_LONG_CLICK : AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    public boolean clickText(String t) { return clickText(t, false); }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = firstEditable(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo input() {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r == null) return null;
        AccessibilityNodeInfo f = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return f != null ? f : firstEditable(r);
    }

    public boolean typeText(String t, boolean append) {
        AccessibilityNodeInfo n = input();
        if (n == null) return false;
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        CharSequence cur = n.getText();
        String base = (append && cur != null && !n.isShowingHintText()) ? cur.toString() : "";
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, base + t);
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    public boolean inputAction(int action) { AccessibilityNodeInfo n = input(); return n != null && n.performAction(action); }

    /** Only called when the user has enabled "read on-screen text" or asked Alex to read the screen. */
    public String screenText() {
        StringBuilder sb = new StringBuilder();
        collect(getRootInActiveWindow(), sb);
        return sb.length() > 1500 ? sb.substring(0, 1500) : sb.toString();
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder sb) {
        if (n == null || sb.length() > 1500 || !n.isVisibleToUser() || n.isPassword()) return;
        CharSequence t = n.getText(), d = n.getContentDescription();
        if (t != null && t.length() > 0) sb.append(t).append(" | ");
        else if (d != null && d.length() > 0) sb.append(d).append(" | ");
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), sb);
    }

    private GestureDescription.StrokeDescription stroke(Path p, long ms) { return new GestureDescription.StrokeDescription(p, 0, ms); }
    private DisplayMetrics dm() { return getResources().getDisplayMetrics(); }

    public void swipe(float x1, float y1, float x2, float y2, long ms) {
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke(p, ms)).build(), null, null);
    }

    public void tapFraction(float fx, float fy, boolean dbl) {
        float x = fx * dm().widthPixels, y = fy * dm().heightPixels;
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder().addStroke(stroke(p, 60)).build();
        dispatchGesture(g, null, null);
        if (dbl) h.postDelayed(() -> dispatchGesture(g, null, null), 130);
    }

    public void scroll(String dir) {
        float w = dm().widthPixels, hh = dm().heightPixels;
        switch (dir) {
            case "down":  swipe(w * .5f, hh * .72f, w * .5f, hh * .28f, 350); break;
            case "up":    swipe(w * .5f, hh * .28f, w * .5f, hh * .72f, 350); break;
            case "left":  swipe(w * .8f, hh * .5f, w * .2f, hh * .5f, 300); break;
            case "right": swipe(w * .2f, hh * .5f, w * .8f, hh * .5f, 300); break;
        }
    }

    public void pinch(boolean zoomIn) {
        float cx = dm().widthPixels / 2f, cy = dm().heightPixels / 2f, s = Math.min(dm().widthPixels, dm().heightPixels) * 0.35f, near = s * 0.15f;
        Path a = new Path(), b = new Path();
        if (zoomIn) { a.moveTo(cx - near, cy); a.lineTo(cx - s, cy); b.moveTo(cx + near, cy); b.lineTo(cx + s, cy); }
        else        { a.moveTo(cx - s, cy); a.lineTo(cx - near, cy); b.moveTo(cx + s, cy); b.lineTo(cx + near, cy); }
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke(a, 400)).addStroke(stroke(b, 400)).build(), null, null);
    }

    public boolean global(int action) { return performGlobalAction(action); }
}
