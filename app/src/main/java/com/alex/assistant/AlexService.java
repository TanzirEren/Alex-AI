package com.alex.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class AlexService extends Service {
    public static boolean running;
    private static final String[] WAKE = {"hey alex", "hi alex", "ok alex", "okay alex", "hello alex", "alex start", "hey alexa", "a alex"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View fab;
    private TextView bubble;
    private WindowManager.LayoutParams lp;
    private OrbAnimator anim;
    private TextToSpeech tts;
    private boolean ttsReady, speaking;
    private SpeechRecognizer rec;
    private int mode; // 0 = waiting for wake word, 1 = listening for command
    private ActionExecutor exec;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (i != null && "stop".equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (!running) { running = true; init(); }
        return START_STICKY;
    }

    private void init() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("alex", "Alex AI", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AlexService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "alex").setContentTitle("Alex is ready")
            .setContentText("Say “Hey Alex”").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open)
            .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_delete), "Stop", stop).build()).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(1, n);
        } catch (Exception e) { stopSelf(); return; }

        exec = new ActionExecutor(this);
        addFab();
        tts = new TextToSpeech(this, st -> {
            if (st != TextToSpeech.SUCCESS) return;
            tts.setLanguage(Locale.US);
            tts.setPitch(1.3f);          // young boy
            tts.setSpeechRate(1.05f);
            try {
                for (Voice v : tts.getVoices()) {
                    String nm2 = v.getName().toLowerCase();
                    boolean male = (nm2.contains("male") && !nm2.contains("female")) || nm2.contains("-iom-") || nm2.contains("-iol-") || nm2.contains("-tpd-");
                    if (male && v.getLocale().getLanguage().equals("en") && !v.isNetworkConnectionRequired()) { tts.setVoice(v); break; }
                }
            } catch (Exception ignored) {}
            ttsReady = true;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String u) {}
                @Override public void onDone(String u) { main.post(() -> { if (!tts.isSpeaking()) { speaking = false; resume(); } }); }
                @Override public void onError(String u) { onDone(u); }
            });
            main.post(() -> { speak(Prefs.key(this).isEmpty() ? "Hi, I am Alex. Add your Gemini key in settings." : "Hi, I'm Alex. Say hey Alex anytime."); });
        });
    }

    // ---------- floating orb ----------
    private void addFab() {
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Allow “Display over other apps” for the floating orb", Toast.LENGTH_LONG).show(); return; }
        wm = getSystemService(WindowManager.class);
        fab = LayoutInflater.from(this).inflate(R.layout.overlay_fab, null);
        bubble = fab.findViewById(R.id.bubble);
        anim = new OrbAnimator(fab.findViewById(R.id.orb), fab.findViewById(R.id.halo));
        anim.idle();
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = 24; lp.y = 420;
        fab.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy, ox, oy; boolean moved, longDone;
            final Runnable lng = () -> { if (!moved) { longDone = true;
                startActivity(new Intent(AlexService.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; moved = false; longDone = false;
                        main.postDelayed(lng, 800); return true;
                    case MotionEvent.ACTION_MOVE: float mx = e.getRawX() - sx, my = e.getRawY() - sy;
                        if (Math.abs(mx) > 14 || Math.abs(my) > 14) { moved = true; main.removeCallbacks(lng); }
                        if (moved) { lp.x = (int) (ox + mx); lp.y = (int) (oy + my); wm.updateViewLayout(fab, lp); } return true;
                    case MotionEvent.ACTION_UP: main.removeCallbacks(lng); if (!moved && !longDone) onOrbTap(); return true;
                }
                return false;
            }
        });
        wm.addView(fab, lp);
    }

    private void onOrbTap() {
        if (speaking && tts != null) { tts.stop(); speaking = false; }
        chime(); main.postDelayed(() -> listen(true), 250);
    }

    private void show(String s) {
        if (bubble == null) return;
        bubble.setText(s); bubble.setVisibility(View.VISIBLE);
        main.removeCallbacks(hide); main.postDelayed(hide, 7000);
    }
    private final Runnable hide = () -> { if (bubble != null) bubble.setVisibility(View.GONE); };

    private void state(int s) {
        if (anim == null) return;
        if (s == 0) anim.idle(); else if (s == 1) anim.listening(); else if (s == 2) anim.thinking(); else anim.speaking();
    }

    private void chime() { try { new ToneGenerator(AudioManager.STREAM_MUSIC, 60).startTone(ToneGenerator.TONE_PROP_ACK, 120); } catch (Exception ignored) {} }

    // ---------- listening ----------
    private void destroyRec() {
        if (rec != null) { rec.setRecognitionListener(null); try { rec.cancel(); rec.destroy(); } catch (Exception ignored) {} rec = null; }
    }

    private void resume() { if (!running) return; if (Prefs.wake(this)) listen(false); else state(0); }

    private void listen(boolean command) {
        main.post(() -> {
            if (!running || speaking) return;
            if (!SpeechRecognizer.isRecognitionAvailable(this)) { show("Speech recognition isn't available on this device"); return; }
            destroyRec();
            mode = command ? 1 : 0;
            state(command ? 1 : 0);
            if (command) show("Listening…");
            rec = SpeechRecognizer.createSpeechRecognizer(this);
            rec.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onEvent(int t, Bundle b) {}
                @Override public void onPartialResults(Bundle b) {}
                @Override public void onError(int e) {
                    if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { show("Microphone permission needed"); return; }
                    if (mode == 1 && e != SpeechRecognizer.ERROR_CLIENT) show("I didn't catch that");
                    main.postDelayed(() -> { if (!speaking && running) resume(); }, e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1500 : 600);
                }
                @Override public void onResults(Bundle b) {
                    ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l == null || l.isEmpty()) { resume(); return; }
                    String text = l.get(0).trim();
                    if (mode == 1) { handle(text); return; }
                    String low = text.toLowerCase(Locale.ROOT);
                    for (String w : WAKE) {
                        int i = low.indexOf(w);
                        if (i >= 0) {
                            String rest = text.substring(i + w.length()).replaceFirst("^[,.\\s]+", "");
                            if (rest.length() > 2) handle(rest); else { chime(); main.postDelayed(() -> listen(true), 250); }
                            return;
                        }
                    }
                    listen(false);
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            rec.startListening(i);
        });
    }

    // ---------- brain ----------
    private void handle(String text) {
        destroyRec();
        show("You: " + text);
        state(2);
        new Thread(() -> {
            JSONObject r = GeminiClient.ask(this, text, context());
            String sp = r.optString("speech");
            JSONArray ac = r.optJSONArray("actions");
            main.post(() -> speak(sp));
            if (ac != null && ac.length() > 0) exec.run(ac, s -> main.post(() -> speak(s)));
        }).start();
    }

    private String context() {
        String time = new SimpleDateFormat("EEEE, d MMM yyyy, h:mm a", Locale.US).format(new Date());
        int bat = getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        String screen = AlexAccessibilityService.active() ? AlexAccessibilityService.instance.screenText() : "(accessibility off)";
        return "Time: " + time + "\nBattery: " + bat + "%\nScreen: " + screen;
    }

    private void speak(String s) {
        if (s == null || s.trim().isEmpty() || !ttsReady) { if (!speaking) resume(); return; }
        destroyRec();
        speaking = true;
        show("Alex: " + s);
        state(3);
        tts.speak(s, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime());
    }

    @Override public void onDestroy() {
        running = false;
        destroyRec();
        main.removeCallbacksAndMessages(null);
        if (anim != null) anim.stop();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        try { if (fab != null && wm != null) wm.removeView(fab); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
