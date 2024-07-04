package me.devsaki.hentoid.core

import android.content.Context
import android.content.Intent
import android.os.Process
import me.devsaki.hentoid.util.logException
import timber.log.Timber
import kotlin.system.exitProcess

class EmergencyRestartHandler(val context: Context, private val myActivityClass: Class<*>) :
    Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {
        Timber.e(e)

        // Log the exception
        Timber.i("Logging crash exception")
        try {
            logException(e)
        } finally {
            // Restart the Activity
            Timber.i("Restart %s", myActivityClass.simpleName)
            val intent = Intent(context, myActivityClass)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
        Timber.i("Kill current process")
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}