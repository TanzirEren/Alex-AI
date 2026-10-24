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
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

/** Executes ONE already-validated action through supported Android APIs. Returns true only if it really started. */
public class ActionExecutor {
    public interface Say { void say(String s); }

    private final Context c;
    private Say say;

    public ActionExecutor(Context c) { this.c = c; }

    private AlexAccessibilityService a() { return AlexAccessibilityService.instance; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
    private static String enc(String s) { return Uri.encode(s); }

    private boolean start(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); return true; } catch (Exception e) { return false; }
    }
    private boolean view(String url) { return start(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private boolean global(int g) { return a() != null && a().global(g); }

    public boolean exec(JSONObject o, Say say) throws Exception {
        this.say = say;
        AlexAccessibilityService s = a();
        String t = o.optString("type");
        switch (t) {
            case "open_app": if (openApp(o.optString("name"))) return true; say.say("I couldn't find an app called " + o.optString("name") + "."); return false;
            case "close_app": say.say("Android doesn't let me close other apps, so I opened Recents. Swipe the app away there."); return global(AccessibilityService.GLOBAL_ACTION_RECENTS);
            case "install_app": return view("market://search?q=" + enc(o.optString("name"))) || view("https://play.google.com/store/search?q=" + enc(o.optString("name")) + "&c=apps");
            case "install_apk": return start(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
            case "uninstall_app": { String p = findPackage(o.optString("name")); if (p == null) { say.say("I couldn't find that app."); return false; }
                return start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + p))); }
            case "app_info": { String p = findPackage(o.optString("name")); if (p == null) { say.say("I couldn't find that app."); return false; }
                return start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p))); }

            case "back": return global(AccessibilityService.GLOBAL_ACTION_BACK);
            case "home": return global(AccessibilityService.GLOBAL_ACTION_HOME);
            case "recents": return global(AccessibilityService.GLOBAL_ACTION_RECENTS);
            case "notifications": return global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
            case "quick_settings": return global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
            case "screenshot": return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
            case "lock": return global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            case "power_menu": return global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);

            case "scroll": s.scroll(o.optString("dir")); return true;
            case "click_text": if (s.clickText(o.optString("text"))) return true; say.say("I couldn't find “" + o.optString("text") + "” on screen."); return false;
            case "long_click_text": return s.clickText(o.optString("text"), true);
            case "type_text": case "chat_type": if (s.typeText(o.optString("text"), false)) return true; say.say("I couldn't find a text box to type in."); return false;
            case "append_text": return s.typeText(o.optString("text"), true);
            case "clear_text": return s.typeText("", false);
            case "copy": return s.inputAction(AccessibilityNodeInfo.ACTION_COPY);
            case "paste": return s.inputAction(AccessibilityNodeInfo.ACTION_PASTE);
            case "select_all": return s.inputAction(AccessibilityNodeInfo.ACTION_SELECT);
            case "tap": s.tapFraction((float) o.optDouble("x"), (float) o.optDouble("y"), false); return true;
            case "double_tap": s.tapFraction((float) o.optDouble("x"), (float) o.optDouble("y"), true); return true;
            case "zoom": s.pinch("in".equals(o.optString("dir"))); return true;
            case "read_screen": { String x = s.screenText(); say.say(x.isEmpty() ? "The screen looks empty." : x.replace(" | ", ", ")); return true; }
            case "wait": sleep(o.optLong("ms", 1000)); return true;

            case "media": return media(o.optString("cmd"));
            case "volume": volume(o.optString("cmd")); return true;
            case "play_music": { Intent i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(android.app.SearchManager.QUERY, o.optString("query"));
                return start(i) || view("https://music.youtube.com/search?q=" + enc(o.optString("query"))); }
            case "youtube_search": { String u = "https://www.youtube.com/results?search_query=" + enc(o.optString("query"));
                return start(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("com.google.android.youtube")) || view(u); }
            case "youtube_music": { String u = "https://music.youtube.com/search?q=" + enc(o.optString("query"));
                return start(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).setPackage("com.google.android.apps.youtube.music")) || view(u); }
            case "open_gallery": return start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
                    || start(new Intent(Intent.ACTION_VIEW).setType("image/*"));
            case "open_camera": return start(new Intent(o.optBoolean("video") ? MediaStore.INTENT_ACTION_VIDEO_CAMERA : MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));

            case "open_url": return view(o.optString("url"));
            case "web_search": return view("https://www.google.com/search?q=" + enc(o.optString("query")));
            case "share": return start(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, o.optString("text")), "Share"));

            case "flashlight": try { CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
                cm.setTorchMode(cm.getCameraIdList()[0], "on".equals(o.optString("state"))); return true; }
                catch (Exception e) { say.say("This device didn't let me use the flashlight."); return false; }
            case "wifi_panel": return start(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) || start(new Intent(Settings.ACTION_WIFI_SETTINGS));
            case "bluetooth_panel": return start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            case "settings": return start(new Intent(settingsAction(o.optString("page"))));
            case "alarm": return start(new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, o.optInt("hour"))
                    .putExtra(AlarmClock.EXTRA_MINUTES, o.optInt("minute")).putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label", "Alex")));
            case "timer": return start(new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, o.optInt("seconds", 60))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true));
            case "open_utility": return utility(o.optString("name"));

            case "call": return call(o.optString("who"));
            case "sms_draft": { String n = number(o.optString("to")); if (n == null) { say.say("I couldn't find that contact's number."); return false; }
                return start(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + n)).putExtra("sms_body", o.optString("text"))); }
            case "whatsapp_send": { String n = number(o.optString("to")); if (n == null) { say.say("I couldn't find that contact's number."); return false; }
                if (!view("https://wa.me/" + n.replace("+", "") + "?text=" + enc(o.optString("text")))) return false;
                sleep(3200);
                if (!s.clickText("Send")) say.say("I opened the message. Please tap Send yourself."); return true; }
            case "chat_open": if (!openApp(o.optString("app"))) { say.say("That chat app isn't installed."); return false; }
                sleep(2800); s.clickText("Search"); sleep(900);
                if (!s.typeText(o.optString("contact"), false)) return false;
                sleep(1600);
                if (!s.clickText(o.optString("contact"))) { say.say("Please pick the contact from the list."); return false; }
                return true;
            case "chat_send": return s.clickText("Send");
            case "chat_call": { boolean v = o.optBoolean("video"); return s.clickText(v ? "Video call" : "Voice call") || s.clickText(v ? "Video" : "Call"); }
            default: return false;
        }
    }

    private boolean utility(String n) {
        switch (n) {
            case "calculator": return start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR));
            case "calendar": return start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR));
            case "contacts": return start(new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI));
            case "maps": return start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS));
            case "browser": return start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
            case "files": return start(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE));
        }
        return false;
    }

    private String findPackage(String name) {
        if (name == null || name.isEmpty()) return null;
        PackageManager pm = c.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        String best = null, nm = name.toLowerCase(); int score = 0;
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

    private boolean media(String cmd) {
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
        return true; // Android gives no result signal: only works if a media session is active.
    }

    private void volume(String cmd) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if ("max".equals(cmd)) { am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI); return; }
        int d = "up".equals(cmd) ? AudioManager.ADJUST_RAISE : "down".equals(cmd) ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_MUTE;
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI);
    }

    private String settingsAction(String page) {
        switch (page.toLowerCase()) {
            case "wifi": return Settings.ACTION_WIFI_SETTINGS;
            case "bluetooth": return Settings.ACTION_BLUETOOTH_SETTINGS;
            case "display": return Settings.ACTION_DISPLAY_SETTINGS;
            case "sound": return Settings.ACTION_SOUND_SETTINGS;
            case "battery": return Intent.ACTION_POWER_USAGE_SUMMARY;
            case "apps": return Settings.ACTION_APPLICATION_SETTINGS;
            case "location": return Settings.ACTION_LOCATION_SOURCE_SETTINGS;
            case "storage": return Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
            case "accessibility": return Settings.ACTION_ACCESSIBILITY_SETTINGS;
            case "airplane": return Settings.ACTION_AIRPLANE_MODE_SETTINGS;
            case "date": return Settings.ACTION_DATE_SETTINGS;
            case "language": return Settings.ACTION_LOCALE_SETTINGS;
            case "network": return Settings.ACTION_WIRELESS_SETTINGS;
            case "hotspot": return "android.settings.TETHER_SETTINGS";
        }
        return Settings.ACTION_SETTINGS;
    }

    private String number(String who) {
        if (who == null) return null;
        if (who.matches("[+0-9 \\-()]{5,}")) return who.replaceAll("[^+0-9]", "");
        if (c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            say.say("Allow contacts access in Settings so I can look up " + who + "."); return null; }
        Cursor cu = c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(who)),
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        try { if (cu != null && cu.moveToFirst()) return cu.getString(0).replaceAll("[^+0-9]", ""); }
        finally { if (cu != null) cu.close(); }
        return null;
    }

    private boolean call(String who) {
        String n = number(who);
        if (n == null) { say.say("I couldn't find " + who + "."); return false; }
        boolean ok = c.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        return start(new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + n)));
    }
}
