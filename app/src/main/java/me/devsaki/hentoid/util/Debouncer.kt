package me.devsaki.hentoid.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Utility class for debouncing values to consumer functions (Kotlin variant that doesn't use Context)
 */
class Debouncer<T>(
    private val scope: CoroutineScope,
    private val delayMs: Long,
    private val callback: (T) -> Unit
) {
    private var taskJob: Job? = null

    fun clear() {
        taskJob?.cancel()
    }

    fun submit(t: T) {
        clear()
        taskJob = scope.launch {
            delay(delayMs)
            callback.invoke(t)
        }
    }
}
