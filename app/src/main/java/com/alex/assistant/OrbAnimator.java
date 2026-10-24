package com.alex.assistant;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.ArrayList;
import java.util.List;

/** Drives the Alex orb through idle / listening / thinking / speaking / executing / error. */
public class OrbAnimator {
    public static final int IDLE = 0, LISTENING = 1, THINKING = 2, SPEAKING = 3, EXECUTING = 4, ERROR = 5;
    private final View orb, halo;
    private final List<Animator> running = new ArrayList<>();

    public OrbAnimator(View orb, View halo) { this.orb = orb; this.halo = halo; }

    private void loop(View v, String prop, float a, float b, long ms, boolean reverse) {
        ObjectAnimator o = ObjectAnimator.ofFloat(v, prop, a, b);
        o.setDuration(ms); o.setRepeatCount(ValueAnimator.INFINITE);
        o.setRepeatMode(reverse ? ValueAnimator.REVERSE : ValueAnimator.RESTART);
        if (!reverse) o.setInterpolator(new LinearInterpolator());
        running.add(o); o.start();
    }

    public void stop() {
        for (Animator a : running) a.cancel();
        running.clear();
        orb.setScaleX(1f); orb.setScaleY(1f); orb.setRotation(0f); orb.setTranslationX(0f);
        halo.setAlpha(0f); halo.setScaleX(1f); halo.setScaleY(1f);
    }

    private void breathe(float to, long ms) { loop(orb, "scaleX", 1f, to, ms, true); loop(orb, "scaleY", 1f, to, ms, true); }
    private void pulse(float to, long ms) { loop(halo, "scaleX", 1f, to, ms, false); loop(halo, "scaleY", 1f, to, ms, false); loop(halo, "alpha", 0.6f, 0f, ms, false); }
    private void spin(long ms) { loop(orb, "rotation", 0f, 360f, ms, false); }

    public void set(int state) {
        stop();
        switch (state) {
            case LISTENING: breathe(1.14f, 600); pulse(1.45f, 1100); spin(9000); break;
            case THINKING:  breathe(1.05f, 700); spin(1300); break;
            case SPEAKING:  breathe(1.12f, 380); pulse(1.35f, 700); spin(6000); break;
            case EXECUTING: breathe(1.08f, 320); pulse(1.25f, 600); spin(2600); break;
            case ERROR: {
                ObjectAnimator s = ObjectAnimator.ofFloat(orb, "translationX", 0, -14, 14, -10, 10, 0).setDuration(420);
                s.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { orb.setTranslationX(0f); breathe(1.06f, 1800); spin(26000); } });
                running.add(s); s.start(); break;
            }
            default: breathe(1.06f, 1800); spin(26000);
        }
    }
}
