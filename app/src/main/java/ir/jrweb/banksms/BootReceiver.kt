package ir.jrweb.banksms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // پس از روشن شدن گوشی، گیرنده پیامک به صورت خودکار توسط سیستم فعال است
    }
}