package me.devsaki.hentoid.parsers

import me.devsaki.hentoid.util.ProgressManager
import java.util.concurrent.atomic.AtomicBoolean

class ParseProgress {
    private var contentId: Long = 0
    private var storedId: Long = 0
    private var hasStarted = false
    private var currentStep = 0
    private lateinit var progressMgr: ProgressManager
    private val processHalted = AtomicBoolean(false)

    fun start(contentId: Long, storedId: Long = -1, maxSteps: Int = 1) {
        this.contentId = contentId
        this.storedId = storedId
        progressMgr = ProgressManager(maxSteps)
        signalProgress(contentId, storedId, 0f)
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

    fun nextStep() {
        progressMgr.setProgress(currentStep.toString(), 1f)
        currentStep++
    }

    fun advance(progress: Float) {
        progressMgr.setProgress(currentStep.toString(), progress)
        signalProgress(contentId, storedId, progressMgr.getGlobalProgress())
    }

    fun complete() {
        signalProgress(contentId, storedId, 1f)
    }
}