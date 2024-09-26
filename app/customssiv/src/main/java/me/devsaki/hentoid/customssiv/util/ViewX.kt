package me.devsaki.hentoid.customssiv.util

import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope

val View.lifecycleScope: CoroutineScope?
    get() = this.findViewTreeLifecycleOwner()?.lifecycleScope