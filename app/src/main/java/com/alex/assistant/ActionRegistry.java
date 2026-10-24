package com.alex.assistant;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ONLY actions Alex can run. Gemini may request these ids; anything else is rejected locally.
 * Param spec: "name" required text | "?name" optional | "name#" number | "name=a|b" enum.
 */
public final class ActionRegistry {
    public static final class Def {
        public final String id, category, desc, status, perm;
        public final boolean sensitive;
        public final String[] params;
        Def(String id, String category, String desc, String status, String perm, boolean sensitive, String[] params) {
            this.id = id; this.category = category; this.desc = desc; this.status = status;
            this.perm = perm; this.sensitive = sensitive; this.params = params;
        }
    }

    private static final Map<String, Def> M = new LinkedHashMap<>();
    private static final String W = "Working", D = "Device-dependent", B = "Best-effort", ACC = "ACCESSIBILITY", NO = "-";

    private static void a(String id, String cat, String desc, String status, String perm, boolean sens, String... params) {
        M.put(id, new Def(id, cat, desc, status, perm, sens, params));
    }

    static {
        a("open_app", "Apps", "Open an installed app by name", W, NO, false, "name");
        a("close_app", "Apps", "Open Recents so you can dismiss an app (Android blocks force-stop)", B, ACC, false);
        a("install_app", "Apps", "Search the Play Store for an app (you tap Install)", W, NO, false, "name");
        a("install_apk", "Apps", "Open Downloads so you can tap an APK; Android shows its own install prompt", W, NO, true);
        a("uninstall_app", "Apps", "Open Android's uninstall screen for an app", W, NO, true, "name");
        a("app_info", "Apps", "Open an app's system info page", W, NO, false, "name");
        a("back", "Navigation", "Press Back", W, ACC, false);
        a("home", "Navigation", "Go to the home screen", W, ACC, false);
        a("recents", "Navigation", "Show recent apps", W, ACC, false);
        a("notifications", "Navigation", "Pull down notifications", W, ACC, false);
        a("quick_settings", "Navigation", "Open quick settings", W, ACC, false);
        a("screenshot", "Navigation", "Take a screenshot", D, ACC, false);
        a("lock", "Navigation", "Lock the screen", D, ACC, false);
        a("power_menu", "Navigation", "Show the power menu", D, ACC, false);
        a("scroll", "Screen", "Scroll or swipe up/down/left/right", W, ACC, false, "dir=up|down|left|right");
        a("click_text", "Screen", "Tap a visible button by its label", B, ACC, false, "text");
        a("long_click_text", "Screen", "Long-press a visible item by its label", B, ACC, false, "text");
        a("type_text", "Screen", "Type into the selected text field", B, ACC, false, "text");
        a("append_text", "Screen", "Add text to the selected field", B, ACC, false, "text");
        a("clear_text", "Screen", "Clear the selected field", B, ACC, false);
        a("copy", "Screen", "Copy selected text", B, ACC, false);
        a("paste", "Screen", "Paste into the selected field", B, ACC, false);
        a("select_all", "Screen", "Select all text in the field", B, ACC, false);
        a("tap", "Screen", "Tap a screen position", B, ACC, false, "x#", "y#");
        a("double_tap", "Screen", "Double-tap a screen position", B, ACC, false, "x#", "y#");
        a("zoom", "Screen", "Pinch zoom in or out in the current app", B, ACC, false, "dir=in|out");
        a("read_screen", "Screen", "Read visible screen text aloud", B, ACC, false);
        a("media", "Media", "Play, pause, next, previous, stop, seek", D, NO, false, "cmd=play|pause|toggle|next|prev|stop|forward|rewind");
        a("volume", "Media", "Volume up, down, mute or max", W, NO, false, "cmd=up|down|mute|max");
        a("play_music", "Media", "Play a song or artist in your music app", D, NO, false, "query");
        a("youtube_search", "Media", "Search YouTube (app or browser)", W, NO, false, "query");
        a("youtube_music", "Media", "Search YouTube Music", W, NO, false, "query");
        a("open_gallery", "Media", "Open your photo gallery", D, NO, false);
        a("open_camera", "Media", "Open the camera or video recorder", W, NO, false, "?video");
        a("open_url", "Web", "Open a web link", W, NO, false, "url");
        a("web_search", "Web", "Search the web", W, NO, false, "query");
        a("share", "Web", "Share text through the Android share sheet", W, NO, false, "text");
        a("flashlight", "Device", "Turn the flashlight on or off", D, NO, false, "state=on|off");
        a("wifi_panel", "Device", "Open the Internet/Wi-Fi panel", W, NO, false);
        a("bluetooth_panel", "Device", "Open Bluetooth settings", W, NO, false);
        a("settings", "Device", "Open a settings page (Wi-Fi, display/brightness, battery, …)", W, NO, false,
            "page=wifi|bluetooth|display|sound|battery|apps|location|storage|accessibility|airplane|date|language|network|hotspot");
        a("alarm", "Device", "Create an alarm (clock app shows it for you to confirm)", W, NO, false, "hour#", "minute#", "?label");
        a("timer", "Device", "Start a timer", W, NO, false, "seconds#");
        a("open_utility", "Device", "Open calculator, calendar, contacts, maps, browser or files", W, NO, false,
            "name=calculator|calendar|contacts|maps|browser|files");
        a("call", "Communication", "Call a contact or number (asks you first)", W, "CONTACTS", true, "who");
        a("sms_draft", "Communication", "Open an SMS draft; you press Send", W, "CONTACTS", false, "to", "text");
        a("whatsapp_send", "Communication", "Send a WhatsApp message (asks you first)", B, ACC, true, "to", "text");
        a("chat_open", "Communication", "Open a chat in WhatsApp, Telegram, Messenger or Instagram by searching the contact", B, ACC, false, "app", "contact");
        a("chat_type", "Communication", "Type a draft into the open chat box (not sent)", B, ACC, false, "text");
        a("chat_send", "Communication", "Press Send in the open chat (asks you first)", B, ACC, true);
        a("chat_call", "Communication", "Start a voice or video call in the open chat (asks you first)", B, ACC, true, "?video");
        a("wait", "Internal", "Pause between steps", W, NO, false, "ms#");
    }

    public static Def get(String id) { return M.get(id); }
    public static Collection<Def> all() { return M.values(); }

    public static int publicCount() {
        int n = 0;
        for (Def d : M.values()) if (!"Internal".equals(d.category)) n++;
        return n;
    }

    /** Compact list for the Gemini system prompt, generated so prompt and allowlist never drift apart. */
    public static String promptList() {
        StringBuilder sb = new StringBuilder();
        for (Def d : M.values()) {
            sb.append(d.id).append('{');
            for (int i = 0; i < d.params.length; i++) { if (i > 0) sb.append(','); sb.append(d.params[i]); }
            sb.append("}; ");
        }
        return sb.toString();
    }
}
