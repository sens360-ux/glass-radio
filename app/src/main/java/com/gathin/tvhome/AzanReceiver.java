package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/**
 * The prayer-time alarm. Plays the azan (AzanActivity) when the TV is on, the prayer is still switched on,
 * the alarm is on time, and no Live TV or HDMI input is in front; then sets the next prayer's alarm.
 * Glass TV may open the azan over another app because its remote-key service is bound.
 */
public class AzanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int prayer = intent.getIntExtra("prayer", -1);
        long at = intent.getLongExtra("at", 0);
        String zone = Azan.prefs(ctx).getString("zone", "");
        try {
            PowerManager pm = ctx.getSystemService(PowerManager.class);
            boolean awake = pm == null || pm.isInteractive();
            boolean onTime = Math.abs(System.currentTimeMillis() - at) <= Azan.LATE_MS;
            boolean masterOn = Azan.isMasterEnabled(ctx);
            boolean prayerOn = prayer >= 0 && Azan.on(ctx, prayer);
            String name = prayer >= 0 && prayer < PrayerTimes.NAMES.length ? PrayerTimes.NAMES[prayer] : "?";
            if (masterOn && prayerOn && awake && onTime) {
                // Wake the TV out of its screensaver first.
                if (pm != null) {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "TvHome:azan");
                    wl.acquire(15_000);
                }
                Intent azanIntent = new Intent(ctx, AzanActivity.class)
                        .putExtra("prayer", prayer)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

                try {
                    ctx.startActivity(azanIntent);
                } catch (Exception e) {
                    Azan.note(ctx, "Direct launch error: " + e.getMessage());
                }

                // Also trigger via high-priority FullScreenIntent notification so system guarantees overlay on all Android versions
                Azan.showFullScreenNotification(ctx, prayer, azanIntent);

                Azan.note(ctx, name + ": azan popup played (in front: " + InputKeyService.front() + ")");
            } else {
                Azan.note(ctx, name + ": skipped, masterOn=" + masterOn + " prayerOn=" + prayerOn
                        + " tvAwake=" + awake + " onTime=" + onTime + " (in front: " + InputKeyService.front() + ")");
            }
        } finally {
            // The next prayer's alarm (this one is now in the past, so it is skipped).
            Azan.schedule(ctx, zone);
        }
    }
}
