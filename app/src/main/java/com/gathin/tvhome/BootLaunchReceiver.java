package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/** Best-effort boot launch. Google TV firmware may restrict background activity launches. */
public class BootLaunchReceiver extends BroadcastReceiver {
    public static final String PREFS = "glass_tv_boot";
    public static final String KEY_AUTORUN = "autorun_enabled";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_AUTORUN, true)) return;
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(launch);
        } catch (RuntimeException e) {
            Log.w("GlassTV.Boot", "Firmware prevented auto-start; select Glass TV as default Home launcher", e);
        }
    }
}
