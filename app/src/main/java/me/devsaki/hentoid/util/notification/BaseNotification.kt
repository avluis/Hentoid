package me.devsaki.hentoid.util.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

private val pendingIntentActivityFlags: Int = if (Build.VERSION.SDK_INT > 30)
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
else PendingIntent.FLAG_UPDATE_CURRENT

private val pendingIntentActionFlags: Int = if (Build.VERSION.SDK_INT > 30)
    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
else PendingIntent.FLAG_CANCEL_CURRENT

abstract class BaseNotification {
    abstract fun onCreateNotification(context: Context): Notification

    fun getPendingIntentForAction(context: Context, clazz: Class<out Any>): PendingIntent {
        val targetIntent = Intent(context, clazz)
        return PendingIntent.getBroadcast(context, 0, targetIntent, pendingIntentActionFlags)
    }

    fun getPendingIntentForActivity(context: Context, clazz: Class<out Any>): PendingIntent {
        val targetIntent = Intent(context, clazz)
        targetIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(context, 0, targetIntent, pendingIntentActivityFlags)
    }
}