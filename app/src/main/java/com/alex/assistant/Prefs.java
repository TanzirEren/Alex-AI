package com.alex.assistant;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("alex", 0); }
    static String key(Context c) { return SecureStore.get(c, "gemini_key"); }
    static void setKey(Context c, String k) { SecureStore.put(c, "gemini_key", k.trim()); }
    static String model(Context c) { return p(c).getString("model", "gemini-2.5-flash"); }
    static void setModel(Context c, String m) { p(c).edit().putString("model", m.trim().isEmpty() ? "gemini-2.5-flash" : m.trim()).apply(); }
    static boolean wake(Context c) { return p(c).getBoolean("wake", false); }
    static boolean speak(Context c) { return p(c).getBoolean("speak", true); }
    static boolean history(Context c) { return p(c).getBoolean("history", true); }
    static boolean screenCtx(Context c) { return p(c).getBoolean("screen_ctx", false); }
    static int rate(Context c) { return p(c).getInt("rate", 105); }
    static int pitch(Context c) { return p(c).getInt("pitch", 130); }
    static String lang(Context c) { return p(c).getString("lang", "en-US"); }
    static String theme(Context c) { return p(c).getString("theme", "system"); }
    static void putBool(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }
    static void putInt(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    static void putStr(Context c, String k, String v) { p(c).edit().putString(k, v).apply(); }
}
