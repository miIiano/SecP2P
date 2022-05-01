package md.miliano.secp2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import md.miliano.secp2p.service.SecP2PHostService;

public class AutoStartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, SecP2PHostService.class);
        context.startForegroundService(service);
        Log.i("Autostart", "started");
    }
}
