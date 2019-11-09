package me.devsaki.hentoid.timber

import android.util.Log

import com.crashlytics.android.Crashlytics

import timber.log.Timber

class CrashlyticsTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int) = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Crashlytics.log(message)
        if (t != null) Crashlytics.logException(t)
    }
}
