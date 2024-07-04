package me.devsaki.hentoid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.annotation.ArrayRes
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp.Companion.isInForeground
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.util.AchievementsManager.trigger
import me.devsaki.hentoid.util.getRandomInt
import org.greenrobot.eventbus.EventBus

class PlugEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isInForeground()) return
        if (null == intent) return

        val action = intent.action
        if (!isInitialStickyBroadcast) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action || Intent.ACTION_POWER_CONNECTED == action) {
                EventBus.getDefault().post(
                    CommunicationEvent(
                        CommunicationEvent.Type.BROADCAST,
                        CommunicationEvent.Recipient.ALL,
                        getRandomQuoteFrom(context, R.array.power_reactions)
                    )
                )
            } else if (Intent.ACTION_HEADSET_PLUG == action
                && intent.getIntExtra("state", 0) > 0
            ) { // "Connect" event
                EventBus.getDefault().post(
                    CommunicationEvent(
                        CommunicationEvent.Type.BROADCAST,
                        CommunicationEvent.Recipient.ALL,
                        getRandomQuoteFrom(context, R.array.audio_reactions)
                    )
                )
            }
        }
    }

    private fun getRandomQuoteFrom(context: Context, @ArrayRes res: Int): String {
        val quotes = context.resources.getStringArray(res)
        val random = getRandomInt(quotes.size)
        if (3 == random && R.array.power_reactions == res) trigger(62)
        return quotes[random]
    }
}