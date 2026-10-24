package com.alex.assistant;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Shows a confirmation card above any app and blocks the worker thread until the user answers. */
public class ConfirmationManager {
    private static final Handler main = new Handler(Looper.getMainLooper());

    /** Call from a background thread only. Returns false on Cancel, timeout, or missing overlay permission. */
    public static boolean ask(Context c, String title, String detail) {
        if (!Settings.canDrawOverlays(c) || Looper.myLooper() == Looper.getMainLooper()) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] yes = {false};
        final View[] holder = new View[1];
        final WindowManager wm = c.getSystemService(WindowManager.class);
        main.post(() -> {
            View v = LayoutInflater.from(new ContextThemeWrapper(c, R.style.AppTheme_Dark)).inflate(R.layout.overlay_confirm, null);
            ((TextView) v.findViewById(R.id.c_title)).setText(title);
            ((TextView) v.findViewById(R.id.c_detail)).setText(detail);
            v.findViewById(R.id.c_yes).setOnClickListener(x -> { yes[0] = true; latch.countDown(); });
            v.findViewById(R.id.c_no).setOnClickListener(x -> latch.countDown());
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT);
            lp.dimAmount = 0.55f; lp.gravity = Gravity.CENTER; lp.horizontalMargin = 0.05f;
            holder[0] = v;
            try { wm.addView(v, lp); } catch (Exception e) { latch.countDown(); }
        });
        try { latch.await(45, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        main.post(() -> { try { if (holder[0] != null) wm.removeView(holder[0]); } catch (Exception ignored) {} });
        return yes[0];
    }
}
