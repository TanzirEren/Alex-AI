package com.alex.assistant;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pipeline: validate → permission check → confirm (if sensitive) → execute → report honestly. */
public class CommandRouter {
    private static final int MAX_STEPS = 6;
    private final Context c;
    private final ActionExecutor ex;

    public CommandRouter(Context c) { this.c = c; this.ex = new ActionExecutor(c); }

    public void run(JSONArray acts, long token, ActionExecutor.Say say) {
        int n = Math.min(acts.length(), MAX_STEPS);
        if (acts.length() > MAX_STEPS) say.say("That's a long chain, so I'll only do the first " + MAX_STEPS + " steps.");
        for (int i = 0; i < n; i++) {
            if (AlexBrain.gen != token) return;                       // cancelled by the user
            JSONObject o = acts.optJSONObject(i);
            if (o == null) continue;
            String err = ActionValidator.validate(o);
            if (err != null) { log("✗", o, err); say.say("I can't do that: " + err + "."); return; }
            ActionRegistry.Def d = ActionRegistry.get(o.optString("type"));

            if ("ACCESSIBILITY".equals(d.perm) && !AlexAccessibilityService.active()) {
                say.say("My accessibility service is off, so I can't do that yet.");
                if (ConfirmationManager.ask(c, "Enable accessibility?", "Open Accessibility settings so you can turn Alex on?"))
                    c.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            if (ActionValidator.isSensitive(o)) {
                if (!Settings.canDrawOverlays(c)) { say.say("Allow display over other apps so I can ask you before doing that."); return; }
                if (!ConfirmationManager.ask(c, "Confirm action", ActionValidator.describe(o))) {
                    log("–", o, "cancelled"); say.say("Okay, I cancelled that."); return;
                }
                if (AlexBrain.gen != token) return;
            }
            boolean ok;
            try { ok = ex.exec(o, say); } catch (Exception e) { ok = false; }
            log(ok ? "✓" : "✗", o, null);
            if (!ok) { say.say("I couldn't complete that step."); return; }
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
    }

    private void log(String mark, JSONObject o, String why) {
        HistoryStore.add(c, "action", mark + " " + ActionValidator.describe(o) + (why == null ? "" : " (" + why + ")"));
    }
}
