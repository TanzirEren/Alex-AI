package com.alex.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private View[] pages; private View[] tabs;
    private OrbAnimator anim;
    private TextView status, hint, btnStart, stMic, stNotif, stOverlay, stAcc, stContacts, btnLang, btnTheme;
    private EditText etMsg, etKey, etModel, etSearch;
    private LinearLayout chatList, actList, capList;
    private ScrollView chatScroll;
    private final Runnable watcher = () -> runOnUiThread(this::renderHistory);
    private final AlexBrain.Listener brainListener = s -> runOnUiThread(() -> { if (anim != null) anim.set(s); });

    private static final String[] LANG = {"en-US", "bn-BD"}, LANG_NAME = {"English", "বাংলা (Bengali)"};
    private static final String[] THEME = {"system", "light", "dark"};

    @Override protected void onCreate(Bundle b) {
        String th = Prefs.theme(this);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme("light".equals(th) || ("system".equals(th) && !night) ? R.style.AppTheme_Light : R.style.AppTheme_Dark);
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        pages = new View[]{findViewById(R.id.page_home), findViewById(R.id.page_activity), findViewById(R.id.page_caps), findViewById(R.id.page_settings)};
        tabs = new View[]{findViewById(R.id.tab0), findViewById(R.id.tab1), findViewById(R.id.tab2), findViewById(R.id.tab3)};
        for (int i = 0; i < 4; i++) { final int k = i; tabs[i].setOnClickListener(v -> showPage(k)); }
        anim = new OrbAnimator(findViewById(R.id.orb), findViewById(R.id.halo));
        status = findViewById(R.id.status_line); hint = findViewById(R.id.tv_hint); btnStart = findViewById(R.id.btn_start);
        chatList = findViewById(R.id.chat_list); actList = findViewById(R.id.act_list); capList = findViewById(R.id.cap_list);
        chatScroll = findViewById(R.id.chat_scroll);
        etMsg = findViewById(R.id.et_msg); etKey = findViewById(R.id.et_key); etModel = findViewById(R.id.et_model); etSearch = findViewById(R.id.et_search);
        stMic = findViewById(R.id.st_mic); stNotif = findViewById(R.id.st_notif); stOverlay = findViewById(R.id.st_overlay);
        stAcc = findViewById(R.id.st_acc); stContacts = findViewById(R.id.st_contacts);
        btnLang = findViewById(R.id.btn_lang); btnTheme = findViewById(R.id.btn_theme);

        // chat input
        findViewById(R.id.btn_send).setOnClickListener(v -> sendTyped());
        etMsg.setOnEditorActionListener((v, id, e) -> { if (id == EditorInfo.IME_ACTION_SEND) { sendTyped(); return true; } return false; });
        findViewById(R.id.btn_mic).setOnClickListener(v -> micTap());
        btnStart.setOnClickListener(v -> toggleService());

        // capabilities
        ((TextView) findViewById(R.id.caps_title)).setText(ActionRegistry.publicCount() + " capabilities");
        etSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { renderCaps(); }
            public void afterTextChanged(Editable s) {}
        });

        // settings
        etKey.setText(""); etKey.setHint(Prefs.key(this).isEmpty() ? "Paste key" : "•••••••• (saved)");
        etModel.setText(Prefs.model(this));
        findViewById(R.id.btn_savekey).setOnClickListener(v -> {
            String k = etKey.getText().toString().trim();
            if (!k.isEmpty()) Prefs.setKey(this, k);
            Prefs.setModel(this, etModel.getText().toString());
            etKey.setText(""); etKey.setHint(Prefs.key(this).isEmpty() ? "Paste key" : "•••••••• (saved)");
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); refresh();
        });
        findViewById(R.id.btn_delkey).setOnClickListener(v -> { Prefs.setKey(this, ""); etKey.setHint("Paste key"); GeminiClient.clear(); refresh(); });
        bindSwitch(R.id.sw_speak, "speak", Prefs.speak(this));
        bindSwitch(R.id.sw_wake, "wake", Prefs.wake(this));
        bindSwitch(R.id.sw_hist, "history", Prefs.history(this));
        bindSwitch(R.id.sw_screen, "screen_ctx", Prefs.screenCtx(this));
        ((Switch) findViewById(R.id.sw_hist)).setOnCheckedChangeListener((v, on) -> { Prefs.putBool(this, "history", on); if (!on) Prefs.p(this).edit().remove("hist").apply(); });
        SeekBar rate = findViewById(R.id.sb_rate), pitch = findViewById(R.id.sb_pitch);
        rate.setProgress(Prefs.rate(this) - 50); pitch.setProgress(Prefs.pitch(this) - 50);
        rate.setOnSeekBarChangeListener(new Seek(v -> Prefs.putInt(this, "rate", v + 50)));
        pitch.setOnSeekBarChangeListener(new Seek(v -> Prefs.putInt(this, "pitch", v + 50)));
        btnLang.setOnClickListener(v -> { int i = (indexOf(LANG, Prefs.lang(this)) + 1) % LANG.length; Prefs.putStr(this, "lang", LANG[i]); refresh(); });
        btnTheme.setOnClickListener(v -> { int i = (indexOf(THEME, Prefs.theme(this)) + 1) % 3; Prefs.putStr(this, "theme", THEME[i]); recreate(); });
        findViewById(R.id.btn_clearhist).setOnClickListener(v -> { HistoryStore.clear(this); GeminiClient.clear(); Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.btn_stopsvc).setOnClickListener(v -> { stopService(new Intent(this, AlexService.class)); AlexService.running = false; refresh(); });
        stMic.setOnClickListener(v -> askPermissions()); stNotif.setOnClickListener(v -> askPermissions()); stContacts.setOnClickListener(v -> askPermissions());
        stOverlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        stAcc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        ((TextView) findViewById(R.id.tv_about)).setText("Alex AI 2.0.0 · Gemini · Java");
        showPage(0); renderCaps();
    }

    private interface IntCb { void on(int v); }
    private static class Seek implements SeekBar.OnSeekBarChangeListener {
        final IntCb cb; Seek(IntCb cb) { this.cb = cb; }
        public void onProgressChanged(SeekBar s, int p, boolean u) { if (u) cb.on(p); }
        public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
    }
    private void bindSwitch(int id, String key, boolean val) {
        Switch s = findViewById(id); s.setChecked(val);
        s.setOnCheckedChangeListener((v, on) -> Prefs.putBool(this, key, on));
    }
    private static int indexOf(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return 0; }

    private void showPage(int idx) {
        for (int i = 0; i < 4; i++) { pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE); tabs[i].setSelected(i == idx); }
        if (idx == 0) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---- actions ----
    private void sendTyped() {
        String t = etMsg.getText().toString().trim();
        if (t.isEmpty()) return;
        etMsg.setText("");
        AlexBrain.submit(this, t);
    }

    private boolean micOk() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    private void micTap() {
        if (!micOk()) { askPermissions(); return; }
        if (!AlexService.running) { startAlex(); chatList.postDelayed(() -> { if (AlexService.instance != null) AlexService.instance.listenNow(); }, 900); }
        else if (AlexService.instance != null) AlexService.instance.listenNow();
    }

    private void toggleService() {
        if (AlexService.running) { stopService(new Intent(this, AlexService.class)); AlexService.running = false; refresh(); return; }
        if (!micOk()) { askPermissions(); return; }
        startAlex();
    }

    private void startAlex() {
        if (!Settings.canDrawOverlays(this)) Toast.makeText(this, "Allow “Display over other apps” for the floating orb and confirmations", Toast.LENGTH_LONG).show();
        startForegroundService(new Intent(this, AlexService.class));
        btnStart.postDelayed(this::refresh, 500);
    }

    private void askPermissions() {
        String[] p = Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE}
            : new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE};
        requestPermissions(p, 1);
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { refresh(); }

    // ---- rendering ----
    private int attr(int a) { TypedValue tv = new TypedValue(); getTheme().resolveAttribute(a, tv, true); return tv.data; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private void renderHistory() {
        chatList.removeAllViews(); actList.removeAllViews();
        JSONArray a = HistoryStore.list(this);
        SimpleDateFormat f = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault());
        int text = attr(R.attr.alexText);
        int text2 = attr(R.attr.alexText2);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            String r = o.optString("r"), t = o.optString("t");
            TextView all = text(t, 13, r.equals("action") ? text2 : text, false);
            all.setText(f.format(new Date(o.optLong("ts"))) + "  ·  " + (r.equals("user") ? "You" : r.equals("alex") ? "Alex" : "Action") + "\n" + t);
            all.setPadding(0, dp(8), 0, dp(8)); actList.addView(all);
            if (r.equals("action")) continue;
            boolean me = r.equals("user");
            TextView m = text(t, 15, me ? 0xFFFFFFFF : text, false);
            m.setBackgroundResource(me ? R.drawable.bubble_user : R.drawable.bubble_alex);
            m.setPadding(dp(14), dp(10), dp(14), dp(10)); m.setMaxWidth(dp(290));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = me ? Gravity.END : Gravity.START; lp.bottomMargin = dp(8);
            chatList.addView(m, lp);
        }
        if (a.length() == 0) { TextView e = text("Say “Hey Alex” or type a message to begin.", 14, text2, false); e.setPadding(0, dp(24), 0, 0); chatList.addView(e); }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void renderCaps() {
        capList.removeAllViews();
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT), cat = "";
        int text = attr(R.attr.alexText);
        int text2 = attr(R.attr.alexText2);
        for (ActionRegistry.Def d : ActionRegistry.all()) {
            if (d.category.equals("Internal")) continue;
            if (!q.isEmpty() && !(d.id + " " + d.desc + " " + d.category).toLowerCase(Locale.ROOT).contains(q)) continue;
            if (!d.category.equals(cat)) { cat = d.category; TextView h = text(cat.toUpperCase(Locale.ROOT), 12, attr(R.attr.alexAccent), true); h.setPadding(dp(4), dp(14), 0, dp(4)); capList.addView(h); }
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setBackgroundResource(R.drawable.glass_card);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.addView(text(d.desc, 15, text, false));
            String need = d.perm.equals("ACCESSIBILITY") ? " · needs Accessibility" : d.perm.equals("CONTACTS") ? " · contacts for names" : "";
            row.addView(text(d.status + need + (d.sensitive ? " · asks you first" : ""), 12, text2, false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(8);
            capList.addView(row, lp);
        }
    }

    private String row(boolean ok, String label, boolean optional) { return (ok ? "✓  " : optional ? "–  " : "○  ") + label + (ok ? "" : "  — tap to enable"); }

    private void refresh() {
        stMic.setText(row(micOk(), "Microphone", false));
        stNotif.setText(row(Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED, "Notifications (listening indicator)", false));
        stOverlay.setText(row(Settings.canDrawOverlays(this), "Display over other apps (orb + confirmations)", false));
        stAcc.setText(row(AlexAccessibilityService.active(), "Accessibility service", false));
        stContacts.setText(row(checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED, "Contacts (optional)", true));
        btnLang.setText("Speech language: " + LANG_NAME[indexOf(LANG, Prefs.lang(this))]);
        btnTheme.setText("Theme: " + Prefs.theme(this));
        boolean on = AlexService.running;
        btnStart.setText(on ? "Stop Alex" : "Start Alex");
        status.setText(on ? (Prefs.wake(this) ? "● mic on" : "● ready") : "○ off");
        hint.setText(Prefs.key(this).isEmpty() ? "Add your Gemini key in Settings to begin." : on ? "Tap the mic or say “Hey Alex”." : "Start Alex to use the floating orb.");
        anim.set(on ? OrbAnimator.LISTENING : OrbAnimator.IDLE);
    }

    @Override protected void onStart() { super.onStart(); HistoryStore.watchers.add(watcher); AlexBrain.listeners.add(brainListener); renderHistory(); }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onStop() { super.onStop(); HistoryStore.watchers.remove(watcher); AlexBrain.listeners.remove(brainListener); anim.stop(); }
}
