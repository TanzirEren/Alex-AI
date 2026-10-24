package com.alex.assistant;

import android.content.Context;
import android.os.BatteryManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** One pipeline for typed and spoken commands: normalize → local intents → Gemini → CommandRouter. */
public class AlexBrain {
    public static volatile long gen = 0;                 // bump to cancel everything in flight
    public interface Listener { void onState(int state); } // 0 idle 2 thinking 4 executing 5 error
    public static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static String lastText = ""; private static long lastTime;

    static void emit(int s) { for (Listener l : listeners) l.onState(s); }

    public static void reply(Context c, String s) {
        if (s == null || s.trim().isEmpty()) return;
        HistoryStore.add(c, "alex", s);
        AlexService.say(s);
    }

    public static void cancel(Context c) {
        gen++;
        AlexService.stopSpeaking();
        emit(0);
    }

    public static synchronized void submit(Context ctx, String raw) {
        final Context c = ctx.getApplicationContext();
        final String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (text.equalsIgnoreCase(lastText) && now - lastTime < 1500) return;   // no duplicate execution
        lastText = text; lastTime = now;
        final long my = ++gen;
        HistoryStore.add(c, "user", text);

        String low = text.toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "");
        if (low.matches("(alex )?(stop|cancel|never ?mind)( that)?")) { cancel(c); reply(c, "Okay, stopped."); return; }
        if (low.contains("what can you do") || low.contains("capabilities")) {
            reply(c, "I know " + ActionRegistry.publicCount() + " actions across apps, media, chat and settings. Open the Skills tab to see each one and what it needs."); return; }
        if (low.matches("(start )?(a )?new (task|chat|conversation)|clear context")) { GeminiClient.clear(); reply(c, "Fresh start. What's next?"); return; }

        emit(2);
        new Thread(() -> {
            JSONObject r = GeminiClient.ask(c, text, context(c), my);
            if (r == null || gen != my) return;
            reply(c, r.optString("speech"));
            if (r.optBoolean("error")) { emit(5); return; }
            JSONArray ac = r.optJSONArray("actions");
            if (ac != null && ac.length() > 0) { emit(4); new CommandRouter(c).run(ac, my, s -> reply(c, s)); }
            if (gen == my) emit(0);
        }).start();
    }

    private static String context(Context c) {
        String time = new SimpleDateFormat("EEEE, d MMM yyyy, h:mm a", Locale.US).format(new Date());
        int bat = c.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        String screen = "(not shared)";
        if (Prefs.screenCtx(c) && AlexAccessibilityService.active()) screen = AlexAccessibilityService.instance.screenText();
        return "Time: " + time + "\nBattery: " + bat + "%\nScreen: " + screen;
    }
}
