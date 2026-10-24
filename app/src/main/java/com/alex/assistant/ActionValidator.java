package com.alex.assistant;

import java.util.Arrays;
import java.util.Locale;
import org.json.JSONObject;

/** Local validation of every AI-requested action. Pure Java so it is unit-testable. */
public final class ActionValidator {
    private static final int MAX = 500;
    private static final String RISKY = "(?i).*\\b(send|call|pay|buy|purchase|delete|remove|uninstall|install|transfer|submit|order|confirm|post|subscribe|allow|accept|sign)\\b.*";

    /** @return null when valid, otherwise a short human-readable reason. */
    public static String validate(JSONObject o) {
        String t = o.optString("type", "");
        ActionRegistry.Def d = ActionRegistry.get(t);
        if (d == null) return "\"" + (t.length() > 30 ? t.substring(0, 30) : t) + "\" isn't a supported action";
        for (String spec : d.params) {
            String p = spec;
            boolean opt = p.startsWith("?"); if (opt) p = p.substring(1);
            boolean num = p.endsWith("#"); if (num) p = p.substring(0, p.length() - 1);
            String[] en = null; int eq = p.indexOf('=');
            if (eq > 0) { en = p.substring(eq + 1).split("\\|"); p = p.substring(0, eq); }
            if (!o.has(p) || o.isNull(p) || String.valueOf(o.opt(p)).trim().isEmpty()) {
                if (opt) continue;
                return "missing " + p;
            }
            String v = String.valueOf(o.opt(p)).trim();
            if (v.length() > MAX) return p + " is too long";
            if (num) {
                double x;
                try { x = Double.parseDouble(v); } catch (Exception e) { return p + " must be a number"; }
                String r = range(t, p, x);
                if (r != null) return r;
            }
            if (en != null && !Arrays.asList(en).contains(v.toLowerCase(Locale.ROOT))) return p + " has an unsupported value";
        }
        if ("open_url".equals(t)) {
            String u = o.optString("url").trim().toLowerCase(Locale.ROOT);
            if (!(u.startsWith("https://") || u.startsWith("http://"))) return "only web links are allowed";
        }
        return null;
    }

    private static String range(String t, String p, double x) {
        if (p.equals("hour") && (x < 0 || x > 23)) return "hour must be 0-23";
        if (p.equals("minute") && (x < 0 || x > 59)) return "minute must be 0-59";
        if (p.equals("seconds") && (x < 1 || x > 86400)) return "timer must be 1 second to 24 hours";
        if ((p.equals("x") || p.equals("y")) && (x < 0 || x > 1)) return p + " must be between 0 and 1";
        if (p.equals("ms") && (x < 0 || x > 6000)) return "wait must be under 6 seconds";
        return null;
    }

    /** Sensitive actions always need an explicit on-screen confirmation. */
    public static boolean isSensitive(JSONObject o) {
        ActionRegistry.Def d = ActionRegistry.get(o.optString("type"));
        if (d == null) return true;
        if (d.sensitive) return true;
        String t = d.id;
        return (t.equals("click_text") || t.equals("long_click_text")) && o.optString("text").matches(RISKY);
    }

    public static String describe(JSONObject o) {
        String t = o.optString("type");
        switch (t) {
            case "call": return "Call " + o.optString("who") + "?";
            case "whatsapp_send": return "Send this WhatsApp message to " + o.optString("to") + ":\n\n“" + o.optString("text") + "”";
            case "chat_send": return "Press Send in the chat that is open now?";
            case "chat_call": return "Start a " + (o.optBoolean("video") ? "video" : "voice") + " call in the open chat?";
            case "install_apk": return "Open Downloads so you can pick an APK. Android will ask you to confirm the install.";
            case "uninstall_app": return "Open the uninstall screen for " + o.optString("name") + "?";
            case "click_text": case "long_click_text": return "Tap “" + o.optString("text") + "” on the screen?";
            default: return t.replace('_', ' ');
        }
    }

    private ActionValidator() {}
}
