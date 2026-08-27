package com.ravanx.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Phone on hote hi wake service chalu (agar user ne on rakhi hai) */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED &&
            Keys(c).wake()) {
            WakeService.start(c)
        }
    }
}
