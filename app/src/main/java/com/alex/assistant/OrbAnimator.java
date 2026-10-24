package com.alex.assistant;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.ArrayList;
import java.util.List;

/** Drives the Alex orb (the FAB image you provided) in 4 states. */
public class OrbAnimator {
    private final View orb, halo;
    private final List<Animator> running = new ArrayList<>();

    public OrbAnimator(View orb, View halo) { this.orb = orb; this.halo = halo; }

    private void loop(View v, String prop, float a, float b, long ms, boolean reverse) {
        ObjectAnimator o = ObjectAnimator.ofFloat(v, prop, a, b);
        o.setDuration(ms);
        o.setRepeatCount(ValueAnimator.INFINITE);
        o.setRepeatMode(reverse ? ValueAnimator.REVERSE : ValueAnimator.RESTART);
        if (!reverse) o.setInterpolator(new LinearInterpolator());
        running.add(o);
        o.start();
    }

    public void stop() {
        for (Animator a : running) a.cancel();
        running.clear();
        orb.setScaleX(1f); orb.setScaleY(1f); halo.setAlpha(0f);
        halo.setScaleX(1f); halo.setScaleY(1f);
    }

    private void spin(long ms) { loop(orb, "rotation", 0f, 360f, ms, false); }
    private void breathe(float to, long ms) {
        loop(orb, "scaleX", 1f, to, ms, true);
        loop(orb, "scaleY", 1f, to, ms, true);
    }
    private void pulse(float to, long ms) {
        loop(halo, "scaleX", 1f, to, ms, false);
        loop(halo, "scaleY", 1f, to, ms, false);
        loop(halo, "alpha", 0.6f, 0f, ms, false);
    }

    public void idle()      { stop(); breathe(1.06f, 1800); spin(26000); }
    public void listening() { stop(); breathe(1.14f, 600); pulse(1.45f, 1100); spin(9000); }
    public void thinking()  { stop(); breathe(1.05f, 700); spin(1300); }
    public void speaking()  { stop(); breathe(1.12f, 380); pulse(1.35f, 700); spin(6000); }
}
