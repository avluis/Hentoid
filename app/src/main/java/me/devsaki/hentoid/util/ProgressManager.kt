package me.devsaki.hentoid.util

import org.apache.commons.collections4.map.HashedMap

class ProgressManager(private val nbSteps: Int) {
    private val steps = HashedMap<String, Float>()

    fun setProgress(step: String, progress: Float) {
        steps[step] = progress
    }

    fun getGlobalProgress(): Float {
        return steps.values.sum() / nbSteps
    }
}