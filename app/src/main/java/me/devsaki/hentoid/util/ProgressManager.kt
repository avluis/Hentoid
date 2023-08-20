package me.devsaki.hentoid.util

class ProgressManager(private val nbSteps: Int) {
    private val steps = HashMap<String, Float>()

    fun setProgress(step: String, progress: Float) {
        steps[step] = progress
    }

    fun getGlobalProgress(): Float {
        return steps.values.sum() / nbSteps
    }
}