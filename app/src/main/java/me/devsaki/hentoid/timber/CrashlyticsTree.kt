package me.devsaki.hentoid.timber

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber


class CrashlyticsTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int) = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (t != null) crashlytics.recordException(t)
        crashlytics.log(message)
    }
}
