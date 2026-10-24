package com.alex.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private View[] pages;
    private ImageView[] tabs;
    private OrbAnimator anim;
    private TextView stPerm, stOverlay, stAcc, stKey, btnStart, status;
    private EditText etKey, etModel;
    private Switch swWake;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        pages = new View[]{findViewById(R.id.page_home), findViewById(R.id.page_features), findViewById(R.id.page_settings)};
        tabs = new ImageView[]{findViewById(R.id.tab0), findViewById(R.id.tab1), findViewById(R.id.tab2)};
        for (int i = 0; i < 3; i++) { final int k = i; tabs[i].setOnClickListener(v -> showPage(k)); }
        showPage(0);

        anim = new OrbAnimator(findViewById(R.id.orb), findViewById(R.id.halo));
        stPerm = findViewById(R.id.st_perm); stOverlay = findViewById(R.id.st_overlay);
        stAcc = findViewById(R.id.st_acc); stKey = findViewById(R.id.st_key);
        btnStart = findViewById(R.id.btn_start); status = findViewById(R.id.status_line);
        etKey = findViewById(R.id.et_key); etModel = findViewById(R.id.et_model); swWake = findViewById(R.id.sw_wake);
        ((TextView) findViewById(R.id.features_text)).setText(getString(R.string.features).replace("\\n", "\n").replaceAll("\n[ \t]+", "\n").trim());

        etKey.setText(Prefs.key(this)); etModel.setText(Prefs.model(this)); swWake.setChecked(Prefs.wake(this));
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            Prefs.save(this, etKey.getText().toString(), etModel.getText().toString(), swWake.isChecked());
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); refresh();
        });
        findViewById(R.id.btn_acc).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.btn_perm).setOnClickListener(v -> askPermissions());
        stPerm.setOnClickListener(v -> askPermissions());
        stOverlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        stAcc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        stKey.setOnClickListener(v -> showPage(2));
        btnStart.setOnClickListener(v -> toggle());
    }

    private void showPage(int idx) {
        for (int i = 0; i < 3; i++) { pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE); tabs[i].setSelected(i == idx); }
    }

    private boolean micOk() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    private void askPermissions() {
        String[] p = Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.CAMERA}
            : new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.CAMERA};
        requestPermissions(p, 1);
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { refresh(); }

    private void toggle() {
        if (AlexService.running) { stopService(new Intent(this, AlexService.class)); AlexService.running = false; refresh(); return; }
        if (!micOk()) { askPermissions(); return; }
        if (!Settings.canDrawOverlays(this)) { stOverlay.performClick(); return; }
        if (Prefs.key(this).isEmpty()) { showPage(2); Toast.makeText(this, "Add your Gemini API key", Toast.LENGTH_LONG).show(); return; }
        startForegroundService(new Intent(this, AlexService.class));
        btnStart.postDelayed(this::refresh, 400);
    }

    private String row(boolean ok, String label) { return (ok ? "✓  " : "○  ") + label + (ok ? "" : "  — tap to fix"); }

    private void refresh() {
        stPerm.setText(row(micOk(), "Microphone & permissions"));
        stOverlay.setText(row(Settings.canDrawOverlays(this), "Floating orb (display over apps)"));
        stAcc.setText(row(AlexAccessibilityService.active(), "Accessibility control"));
        stKey.setText(row(!Prefs.key(this).isEmpty(), "Gemini API key"));
        boolean on = AlexService.running;
        btnStart.setText(on ? "Stop Alex" : "Start Alex");
        status.setText(on ? "Alex is listening — say “Hey Alex”" : "Tap Start, then say “Hey Alex”");
        if (on) anim.listening(); else anim.idle();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onPause() { super.onPause(); anim.stop(); }
}
