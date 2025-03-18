package me.devsaki.hentoid.events

import androidx.annotation.IdRes
import androidx.documentfile.provider.DocumentFile

/**
 * Tracks processing events (e.g. import, migration) for interested subscribers.
 */
class ProcessEvent {

    enum class Type {
        NONE,
        PROGRESS,   // Processing in progress (1 element done)
        COMPLETE,   // Processing complete (possibly with a % of errors)
        FAILURE,    // Processing failed entirely
    }

    val eventType: Type // Event type

    val processId: Int // Identifier of the process the event is used for

    val step: Int // Step of the process

    var elementName: String? = null // Name of processed element

    var elementsOK = 0 // Number of elements that have been correctly processed

    var elementsOKOther = 0 // Number of other elements that have been correctly processed

    // use case : the same processing activity processes two different elements
    var elementsKO = 0 // Number of elements whose processing has failed

    var elementsTotal = 0 // Number of elements to process

    private var _progressPc = -1f

    var logFile: DocumentFile? = null // Log file, if exists (for EventType.COMPLETE)

    val progressPc: Float
        get() {
            return if (_progressPc < 0f) {
                if (elementsTotal > 0) 1f * (elementsOK + elementsKO) / elementsTotal
                else -1f
            } else _progressPc
        }


    /**
     * Use for indefinite EventType.PROGRESS events
     *
     * @param eventType event type code
     * @param step      step of the  process
     */
    constructor(
        eventType: Type,
        @IdRes processId: Int,
        step: Int,
        elementName: String?
    ) {
        this.eventType = eventType
        this.processId = processId
        this.step = step
        this.elementName = elementName
        logFile = null
        elementsOK = -1
        elementsOKOther = -1
        elementsKO = -1
        elementsTotal = -1
    }

    /**
     * Use for definite EventType.PROGRESS events
     *
     * @param eventType     event type code
     * @param step          step of the  process
     * @param elementsOK    elements processed successfully so far
     * @param elementsKO    elements whose processing has failed so far
     * @param elementsTotal total elements to process
     */
    constructor(
        eventType: Type,
        @IdRes processId: Int,
        step: Int,
        elementsOK: Int,
        elementsKO: Int,
        elementsTotal: Int
    ) {
        this.eventType = eventType
        this.processId = processId
        this.step = step
        this.elementsOK = elementsOK
        elementsOKOther = -1
        this.elementsKO = elementsKO
        this.elementsTotal = elementsTotal
        logFile = null
        elementName = ""
    }

    /**
     * Use for indefinite EventType.PROGRESS events
     *
     * @param eventType     event type code
     * @param step          step of the  process
     * @param elementsOK    elements processed successfully so far
     * @param elementsKO    elements whose processing has failed so far
     * @param progressPc    Progress as a percentage (0f = 0%; 1f = 100%=
     */
    constructor(
        eventType: Type,
        @IdRes processId: Int,
        step: Int,
        elementsOK: Int,
        elementsKO: Int,
        progressPc: Float
    ) {
        this.eventType = eventType
        this.processId = processId
        this.step = step
        this.elementsOK = elementsOK
        elementsOKOther = -1
        this.elementsKO = elementsKO
        this._progressPc = progressPc
        logFile = null
        elementName = ""
    }

    /**
     * Use for definite EventType.PROGRESS events
     *
     * @param eventType     event type code
     * @param step          step of the process
     * @param name          name of the element being processed
     * @param elementsOK    elements processed successfully so far
     * @param elementsKO    elements whose processing has failed so far
     * @param elementsTotal total elements to process
     */
    constructor(
        eventType: Type,
        @IdRes processId: Int,
        step: Int,
        name: String,
        elementsOK: Int,
        elementsKO: Int,
        elementsTotal: Int
    ) {
        this.eventType = eventType
        this.processId = processId
        this.step = step
        this.elementsOK = elementsOK
        elementsOKOther = -1
        this.elementsKO = elementsKO
        this.elementsTotal = elementsTotal
        logFile = null
        elementName = name
    }

    /**
     * Use for EventType.COMPLETE events with a log
     *
     * @param eventType     event type code
     * @param step          step of the process
     * @param elementsOK    elements processed successfully so far
     * @param elementsKO    elements whose processing has failed so far
     * @param elementsTotal total elements to process
     */
    constructor(
        eventType: Type,
        @IdRes processId: Int,
        step: Int,
        elementsOK: Int,
        elementsKO: Int,
        elementsTotal: Int,
        logFile: DocumentFile?
    ) {
        this.eventType = eventType
        this.processId = processId
        this.step = step
        this.elementsOK = elementsOK
        elementsOKOther = -1
        this.elementsKO = elementsKO
        this.elementsTotal = elementsTotal
        this.logFile = logFile
        elementName = ""
    }
}