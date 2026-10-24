package com.alex.assistant;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("alex", 0); }
    static String key(Context c) { return p(c).getString("key", ""); }
    static String model(Context c) { return p(c).getString("model", "gemini-2.5-flash"); }
    static boolean wake(Context c) { return p(c).getBoolean("wake", true); }
    static void save(Context c, String k, String m, boolean w) {
        p(c).edit().putString("key", k.trim())
            .putString("model", m.trim().isEmpty() ? "gemini-2.5-flash" : m.trim())
            .putBoolean("wake", w).apply();
    }
}
