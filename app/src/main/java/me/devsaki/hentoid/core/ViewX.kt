package me.devsaki.hentoid.core

import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat

fun <T : View, U : View> T.requireById(@IdRes resId: Int): U {
    return ViewCompat.requireViewById(this, resId)
}