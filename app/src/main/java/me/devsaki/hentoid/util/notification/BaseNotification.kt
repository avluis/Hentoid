package me.devsaki.hentoid.util.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

abstract class BaseNotification {
    abstract fun onCreateNotification(context: Context): Notification

    fun getPendingIntentForAction(context: Context, clazz: Class<out Any>): PendingIntent {
        return getPendingIntent(context, Intent(context, clazz))
    }

    fun getPendingIntentForActivity(context: Context, clazz: Class<out Any>): PendingIntent {
        val targetIntent = Intent(context, clazz)
        targetIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return getPendingIntent(context, targetIntent)
    }

    private fun getPendingIntent(context: Context, targetIntent: Intent): PendingIntent {
        val flags =
            if (Build.VERSION.SDK_INT > 30)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, 0, targetIntent, flags)
    }
}