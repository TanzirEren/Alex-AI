package com.alex.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;

/** Turns Gemini's JSON actions into real phone control. */
public class ActionExecutor {
    public interface Say { void say(String s); }

    private final Context c;
    private Say say;

    public ActionExecutor(Context c) { this.c = c; }

    private AlexAccessibilityService a() { return AlexAccessibilityService.instance; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    public void run(JSONArray acts, Say say) {
        this.say = say;
        for (int i = 0; i < acts.length(); i++) {
            JSONObject o = acts.optJSONObject(i);
            if (o == null) continue;
            try { exec(o); } catch (Exception ignored) {}
            sleep(450);
        }
    }

    private boolean need() {
        if (a() != null) return true;
        say.say("Please enable my accessibility service first.");
        return false;
    }

    private boolean start(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); return true; }
        catch (Exception e) { return false; }
    }

    private boolean view(String url) { return start(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private static String enc(String s) { return Uri.encode(s); }

    private void exec(JSONObject o) throws Exception {
        String t = o.optString("type");
        AlexAccessibilityService s = a();
        switch (t) {
            // ----- apps -----
            case "open_app": if (!openApp(o.optString("name"))) say.say("I couldn't find " + o.optString("name")); break;
            case "close_app": if (need()) { s.global(AccessibilityService.GLOBAL_ACTION_RECENTS); sleep(900);
                float w = c.getResources().getDisplayMetrics().widthPixels, h = c.getResources().getDisplayMetrics().heightPixels;
                s.swipe(w * .5f, h * .65f, w * .5f, h * .1f, 250); sleep(500); s.global(AccessibilityService.GLOBAL_ACTION_HOME); } break;
            case "back": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_BACK); break;
            case "home": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_HOME); break;
            case "recents": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_RECENTS); break;
            case "notifications": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); break;
            case "quick_settings": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); break;
            case "screenshot": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); break;
            case "lock": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); break;
            case "power_menu": if (need()) s.global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG); break;
            case "uninstall_app": { String p = findPackage(o.optString("name"));
                if (p == null) { say.say("I couldn't find that app."); break; }
                start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + p)));
                if (a() != null) { sleep(1500); if (!a().clickText("OK")) a().clickText("Uninstall"); } break; }
            case "install_app": view("market://search?q=" + enc(o.optString("name")));
                if (a() != null) { sleep(3500); a().clickText("Install"); } break;
            case "install_apk": { Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("application/vnd.android.package-archive");
                start(Intent.createChooser(i, "Pick APK")); break; }

            // ----- screen interaction -----
            case "click_text": if (need() && !s.clickText(o.optString("text"))) say.say("I couldn't find " + o.optString("text") + " on screen."); break;
            case "long_click_text": if (need()) s.clickText(o.optString("text"), true); break;
            case "type_text": case "chat_type": if (need() && !s.typeText(o.optString("text"), false)) say.say("I couldn't find a text box."); break;
            case "append_text": if (need()) s.typeText(o.optString("text"), true); break;
            case "clear_text": if (need()) s.typeText("", false); break;
            case "copy": if (need()) s.inputAction(AccessibilityNodeInfo.ACTION_COPY); break;
            case "paste": if (need()) s.inputAction(AccessibilityNodeInfo.ACTION_PASTE); break;
            case "select_all": if (need()) s.inputAction(AccessibilityNodeInfo.ACTION_SELECT); break;
            case "scroll": case "swipe": if (need()) s.scroll(o.optString("dir", "down")); break;
            case "tap": if (need()) s.tapFraction((float) o.optDouble("x", .5), (float) o.optDouble("y", .5), false); break;
            case "double_tap": if (need()) s.tapFraction((float) o.optDouble("x", .5), (float) o.optDouble("y", .5), true); break;
            case "zoom": if (need()) s.pinch("in".equals(o.optString("dir"))); break;
            case "read_screen": if (need()) { String x = s.screenText(); say.say(x.isEmpty() ? "The screen looks empty." : x.replace(" | ", ", ")); } break;
            case "wait": sleep(Math.min(o.optLong("ms", 1000), 6000)); break;

            // ----- media -----
            case "media": media(o.optString("cmd")); break;
            case "volume": volume(o.optString("cmd")); break;
            case "play_music": { Intent i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(android.app.SearchManager.QUERY, o.optString("query"));
                if (!start(i)) view("https://music.youtube.com/search?q=" + enc(o.optString("query"))); break; }
            case "youtube_search": ytSearch(o.optString("query")); break;
            case "youtube_play": ytSearch(o.optString("query")); sleep(3800);
                if (a() != null && !a().clickContaining("play video")) a().tapFraction(.5f, .3f, false); break;
            case "youtube_category": openApp("YouTube"); sleep(2800);
                if (a() == null || !a().clickText(o.optString("name"))) ytSearch(o.optString("name")); break;
            case "open_gallery": { Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY);
                if (!start(i)) start(new Intent(Intent.ACTION_VIEW).setType("image/*")); break; }
            case "open_camera": start(new Intent(o.optBoolean("video") ? MediaStore.INTENT_ACTION_VIDEO_CAMERA : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); break;

            // ----- system -----
            case "flashlight": { CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
                cm.setTorchMode(cm.getCameraIdList()[0], "on".equals(o.optString("state"))); break; }
            case "wifi_panel": if (!start(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))) start(new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
            case "bluetooth_panel": start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
            case "settings": settings(o.optString("page")); break;
            case "open_url": view(o.optString("url")); break;
            case "web_search": view("https://www.google.com/search?q=" + enc(o.optString("query"))); break;
            case "alarm": start(new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, o.optInt("hour"))
                .putExtra(AlarmClock.EXTRA_MINUTES, o.optInt("minute")).putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label", "Alex"))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)); break;
            case "timer": start(new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, o.optInt("seconds", 60))
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)); break;
            case "share": start(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, o.optString("text")), "Share")); break;

            // ----- calls & chat -----
            case "call": call(o.optString("who")); break;
            case "sms": sms(o.optString("to"), o.optString("text")); break;
            case "whatsapp_send": { String n = number(o.optString("to"));
                if (n == null) { say.say("I couldn't find that contact."); break; }
                view("https://wa.me/" + n.replace("+", "") + "?text=" + enc(o.optString("text")));
                sleep(3200); if (a() != null) { if (!a().clickText("Send")) a().tapFraction(.93f, .93f, false); } break; }
            case "chat_open": openApp(o.optString("app")); sleep(2800);
                if (need()) { s = a(); s.clickText("Search"); sleep(900); s.typeText(o.optString("contact"), false); sleep(1600);
                    if (!s.clickText(o.optString("contact"))) s.tapFraction(.5f, .25f, false); } break;
            case "chat_send": if (need() && !a().clickText("Send")) a().clickContaining("send"); break;
            case "chat_call": if (need()) { boolean v = o.optBoolean("video"); if (!a().clickText(v ? "Video call" : "Voice call")) a().clickText(v ? "Video" : "Call"); } break;
            default: break;
        }
    }

    // ---------- helpers ----------
    private void ytSearch(String q) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + enc(q))).setPackage("com.google.android.youtube");
        if (!start(i)) view("https://www.youtube.com/results?search_query=" + enc(q));
    }

    private String findPackage(String name) {
        if (name == null || name.isEmpty()) return null;
        PackageManager pm = c.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        String best = null; int score = 0, n = name.toLowerCase().length();
        String nm = name.toLowerCase();
        for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            String l = r.loadLabel(pm).toString().toLowerCase();
            int sc = l.equals(nm) ? 3 : l.startsWith(nm) ? 2 : (l.contains(nm) || (l.length() > 2 && nm.contains(l))) ? 1 : 0;
            if (sc > score) { score = sc; best = r.activityInfo.packageName; }
        }
        return best;
    }

    private boolean openApp(String name) {
        String p = findPackage(name);
        if (p == null) return false;
        Intent i = c.getPackageManager().getLaunchIntentForPackage(p);
        return i != null && start(i);
    }

    private void media(String cmd) {
        int k;
        switch (cmd) {
            case "play": k = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case "pause": k = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            case "next": k = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case "prev": k = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            case "stop": k = KeyEvent.KEYCODE_MEDIA_STOP; break;
            case "forward": k = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
            case "rewind": k = KeyEvent.KEYCODE_MEDIA_REWIND; break;
            default: k = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        }
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, k));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, k));
    }

    private void volume(String cmd) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int d = AudioManager.ADJUST_SAME;
        if ("up".equals(cmd)) d = AudioManager.ADJUST_RAISE;
        else if ("down".equals(cmd)) d = AudioManager.ADJUST_LOWER;
        else if ("mute".equals(cmd)) d = AudioManager.ADJUST_MUTE;
        if ("max".equals(cmd)) am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI);
        else am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI);
    }

    private void settings(String page) {
        String a = Settings.ACTION_SETTINGS;
        switch (page == null ? "" : page.toLowerCase()) {
            case "wifi": a = Settings.ACTION_WIFI_SETTINGS; break;
            case "bluetooth": a = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "display": a = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "sound": a = Settings.ACTION_SOUND_SETTINGS; break;
            case "battery": a = Intent.ACTION_POWER_USAGE_SUMMARY; break;
            case "apps": a = Settings.ACTION_APPLICATION_SETTINGS; break;
            case "location": a = Settings.ACTION_LOCATION_SOURCE_SETTINGS; break;
            case "storage": a = Settings.ACTION_INTERNAL_STORAGE_SETTINGS; break;
            case "accessibility": a = Settings.ACTION_ACCESSIBILITY_SETTINGS; break;
            case "airplane": a = Settings.ACTION_AIRPLANE_MODE_SETTINGS; break;
            case "date": a = Settings.ACTION_DATE_SETTINGS; break;
            case "language": a = Settings.ACTION_LOCALE_SETTINGS; break;
        }
        start(new Intent(a));
    }

    private String number(String who) {
        if (who == null) return null;
        if (who.matches("[+0-9 \\-()]{5,}")) return who.replaceAll("[^+0-9]", "");
        if (c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        Cursor cu = c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(who)),
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        try { if (cu != null && cu.moveToFirst()) return cu.getString(0).replaceAll("[^+0-9]", ""); }
        finally { if (cu != null) cu.close(); }
        return null;
    }

    private void call(String who) {
        String n = number(who);
        if (n == null) { say.say("I couldn't find " + who + " in your contacts."); start(new Intent(Intent.ACTION_DIAL)); return; }
        boolean ok = c.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        start(new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + n)));
    }

    @SuppressWarnings("deprecation")
    private void sms(String to, String text) {
        String n = number(to);
        if (n == null || c.checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            say.say("I need the contact and SMS permission for that."); return; }
        SmsManager sm = Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
        sm.sendMultipartTextMessage(n, null, sm.divideMessage(text), null, null);
    }
}
