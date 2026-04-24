package eu.kodanetwork.mchost.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        Intent si = new Intent(ctx, TermuxServerService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(si);
        else ctx.startService(si);
    }
}
