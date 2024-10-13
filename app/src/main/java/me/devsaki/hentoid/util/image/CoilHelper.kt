package me.devsaki.hentoid.util.image

import android.content.Context
import coil3.imageLoader

fun clearCoilCache(context: Context, memory: Boolean = true, file: Boolean = true) {
    val imageLoader = context.imageLoader
    if (memory) imageLoader.memoryCache?.clear()
    if (file) imageLoader.diskCache?.clear()
}