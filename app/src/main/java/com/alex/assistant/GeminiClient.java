package com.alex.assistant;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class GeminiClient {
    private static final JSONArray hist = new JSONArray();

    static final String SYS =
        "You are Alex, a friendly, upbeat young male voice assistant that controls an Android phone. "
        + "Reply with ONLY a JSON object: {\"speech\":\"short natural reply, max 2 sentences\",\"actions\":[{\"type\":\"...\",...}]}. "
        + "Use [] for actions when the user only chats. Match the user's language. Actions run in order. "
        + "Add {\"type\":\"wait\",\"ms\":1500} after launching apps before interacting with them. "
        + "ACTION TYPES (params): "
        + "open_app{name}; close_app{}; back{}; home{}; recents{}; notifications{}; quick_settings{}; screenshot{}; lock{}; power_menu{}; "
        + "click_text{text}; long_click_text{text}; type_text{text}; append_text{text}; clear_text{}; copy{}; paste{}; select_all{}; "
        + "scroll{dir:up|down|left|right}; swipe{dir}; tap{x,y as 0-1 fractions}; double_tap{x,y}; zoom{dir:in|out}; "
        + "media{cmd:play|pause|toggle|next|prev|stop|forward|rewind}; volume{cmd:up|down|mute|max}; play_music{query}; "
        + "youtube_search{query}; youtube_play{query}; youtube_category{name}; open_url{url}; web_search{query}; "
        + "open_gallery{}; open_camera{video:true|false}; flashlight{state:on|off}; wifi_panel{}; bluetooth_panel{}; settings{page}; "
        + "call{who}; sms{to,text}; whatsapp_send{to,text}; chat_open{app,contact}; chat_type{text}; chat_send{}; chat_call{video:true|false}; "
        + "install_app{name}; install_apk{}; uninstall_app{name}; alarm{hour,minute,label}; timer{seconds}; share{text}; read_screen{}. "
        + "RULES: To message someone on WhatsApp/Telegram/Messenger/Instagram use chat_open, then wait, chat_type, chat_send. "
        + "The user message includes the current time, battery and visible screen text; use it to choose exact click_text labels and to answer time/battery questions in speech. "
        + "Never invent actions outside this list.";

    private static JSONObject turn(String role, String text) throws Exception {
        return new JSONObject().put("role", role).put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    private static JSONObject err(String m) {
        try { return new JSONObject().put("speech", m).put("actions", new JSONArray()); }
        catch (Exception e) { return new JSONObject(); }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) sb.append(l);
        return sb.toString();
    }

    private static void dropLastUser() {
        if (hist.length() > 0) hist.remove(hist.length() - 1);
    }

    public static synchronized JSONObject ask(Context c, String cmd, String ctx) {
        String key = Prefs.key(c), model = Prefs.model(c);
        if (key.isEmpty()) return err("Please add your Gemini API key in settings first.");
        try {
            hist.put(turn("user", "Command: " + cmd + "\n" + ctx));
            JSONObject body = new JSONObject();
            body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", SYS))));
            body.put("contents", hist);
            JSONObject gc = new JSONObject().put("responseMimeType", "application/json").put("temperature", 0.3);
            if (model.contains("2.5-flash")) gc.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
            body.put("generationConfig", gc);

            HttpURLConnection h = (HttpURLConnection) new URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent").openConnection();
            h.setRequestMethod("POST");
            h.setConnectTimeout(15000); h.setReadTimeout(30000); h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("x-goog-api-key", key);
            h.getOutputStream().write(body.toString().getBytes("UTF-8"));
            int code = h.getResponseCode();
            String resp = read(code < 400 ? h.getInputStream() : h.getErrorStream());
            if (code >= 400) { dropLastUser(); return err("Gemini returned error " + code + ". Please check your API key and model."); }

            String text = new JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
            text = text.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            JSONObject out = new JSONObject(text);
            hist.put(turn("model", text));
            while (hist.length() > 8) { hist.remove(0); hist.remove(0); }
            return out;
        } catch (Exception e) {
            try { if (hist.length() % 2 == 1) dropLastUser(); } catch (Exception ignored) {}
            return err("Sorry, I couldn't reach Gemini. Check your internet.");
        }
    }
}
