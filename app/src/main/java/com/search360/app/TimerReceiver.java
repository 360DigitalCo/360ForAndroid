package com.search360.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when the timer's alarm goes off, even if the app was closed. */
public class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Context app = ctx.getApplicationContext();
        if (Store.p == null) Store.init(app);
        String act = intent == null ? null : intent.getAction();
        if (TimerNotif.ACT_TOGGLE.equals(act)) TimerCore.toggle(app);
        else if (TimerNotif.ACT_SKIP.equals(act)) TimerCore.skip(app);
        else if (TimerNotif.ACT_RESET.equals(act)) TimerCore.reset(app);
        else TimerCore.finishIfDue(app, true);
    }
}
