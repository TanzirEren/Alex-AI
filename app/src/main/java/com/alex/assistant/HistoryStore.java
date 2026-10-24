package com.alex.assistant;

import android.content.Context;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Conversation + action log. Persisted only when the user keeps history on. */
public class HistoryStore {
    public static final CopyOnWriteArrayList<Runnable> watchers = new CopyOnWriteArrayList<>();
    private static JSONArray mem;

    private static synchronized JSONArray load(Context c) {
        if (mem == null) {
            try { mem = Prefs.history(c) ? new JSONArray(Prefs.p(c).getString("hist", "[]")) : new JSONArray(); }
            catch (Exception e) { mem = new JSONArray(); }
        }
        return mem;
    }

    /** role: user | alex | action */
    public static synchronized void add(Context c, String role, String text) {
        try {
            JSONArray a = load(c);
            a.put(new JSONObject().put("r", role).put("t", text).put("ts", System.currentTimeMillis()));
            while (a.length() > 120) a.remove(0);
            if (Prefs.history(c)) Prefs.putStr(c, "hist", a.toString());
        } catch (Exception ignored) {}
        for (Runnable r : watchers) r.run();
    }

    public static synchronized JSONArray list(Context c) { return load(c); }

    public static synchronized void clear(Context c) {
        mem = new JSONArray();
        Prefs.p(c).edit().remove("hist").apply();
        for (Runnable r : watchers) r.run();
    }
}
