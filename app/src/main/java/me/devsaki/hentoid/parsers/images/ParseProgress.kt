package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.parsers.ParseHelper
import java.util.concurrent.atomic.AtomicBoolean

class ParseProgress {
    private var contentId: Long = 0
    private var storedId: Long = 0
    private var currentStep = 0
    private var maxSteps = 0
    private var hasStarted = false
    private val processHalted = AtomicBoolean(false)

    fun start(contentId: Long, storedId: Long, maxSteps: Int) {
        this.contentId = contentId
        this.storedId = storedId
        currentStep = 0
        this.maxSteps = maxSteps
        ParseHelper.signalProgress(contentId, storedId, currentStep, maxSteps)
        hasStarted = true
    }

    fun hasStarted(): Boolean {
        return hasStarted
    }

    fun isProcessHalted(): Boolean {
        return processHalted.get()
    }

    fun haltProcess() {
        processHalted.set(true)
    }

    fun advance() {
        ParseHelper.signalProgress(contentId, storedId, ++currentStep, maxSteps)
    }

    fun complete() {
        ParseHelper.signalProgress(contentId, storedId, maxSteps, maxSteps)
    }
}