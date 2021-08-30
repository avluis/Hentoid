package me.devsaki.hentoid.core

import android.os.Bundle
import androidx.fragment.app.Fragment

fun <T : Fragment> T.withArguments(bundleBlock: Bundle.() -> Unit): T {
    arguments = Bundle().apply(bundleBlock)
    return this
}