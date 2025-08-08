package me.devsaki.hentoid.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class ProgressManager(private val nbSteps: Int = 1) {
    private val steps = ConcurrentHashMap<String, Float>()

    fun setProgress(step: String, progress: Float) {
        steps[step] = progress
    }

    fun getGlobalProgress(): Float {
        return steps.values.sum() / max(nbSteps, steps.size)
    }
}