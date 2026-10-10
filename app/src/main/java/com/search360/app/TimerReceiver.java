package com.search360.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when the timer's alarm goes off, even if the app was closed. */
public class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Context app = ctx.getApplicationContext();
        if (Store.p == null) Store.init(app);
        TimerCore.finishIfDue(app, true);
    }
}
