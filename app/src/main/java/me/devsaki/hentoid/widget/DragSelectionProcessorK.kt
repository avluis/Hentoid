package me.devsaki.hentoid.widget

/**
 * Credits go to https://github.com/MFlisar/DragSelectRecyclerView for the pre-Jetpack version
 */
class DragSelectionProcessorK(private val selectionHandler: ISelectionHandler) :
    DragSelectTouchListenerK.OnAdvancedDragSelectListener {
    /**
     * Different existing selection modes
     */
    enum class Mode {
        /**
         * simply selects each item you go by and unselects on move back
         */
        Simple,

        /**
         * toggles each items original state, reverts to the original state on move back
         */
        ToggleAndUndo,

        /**
         * toggles the first item and applies the same state to each item you go by and applies inverted state on move back
         */
        FirstItemDependent,

        /**
         * toggles the item and applies the same state to each item you go by and reverts to the original state on move back
         */
        FirstItemDependentToggleAndUndo
    }

    private var mMode: Mode
    private var mStartFinishedListener: ISelectionStartFinishedListener? = null
    private var mOriginalSelection: HashSet<Int>? = null
    private var mFirstWasSelected = false
    private var mCheckSelectionState = false

    init {
        mMode = Mode.Simple
        mStartFinishedListener = null
    }

    /**
     * @param mode the mode in which the selection events should be processed
     * @return this
     */
    fun withMode(mode: Mode): DragSelectionProcessorK {
        mMode = mode
        return this
    }

    /**
     * @param startFinishedListener a listener that get's notified when the drag selection is started or finished
     * @return this
     */
    fun withStartFinishedListener(startFinishedListener: ISelectionStartFinishedListener): DragSelectionProcessorK {
        mStartFinishedListener = startFinishedListener
        return this
    }

    /**
     * If this is enabled, the processor will check if an items selection state is toggled before notifying the [ISelectionHandler]
     *
     * @param check true, if this check should be enabled
     * @return this
     */
    fun withCheckSelectionState(check: Boolean): DragSelectionProcessorK {
        mCheckSelectionState = check
        return this
    }

    override fun onSelectionStarted(start: Int) {
        mOriginalSelection = HashSet()
        val selected = selectionHandler.selection
        if (selected != null) mOriginalSelection!!.addAll(selected)
        mFirstWasSelected = mOriginalSelection!!.contains(start)
        when (mMode) {
            Mode.Simple -> {
                selectionHandler.updateSelection(
                    start, start,
                    isSelected = true,
                    calledFromOnStart = true
                )
            }

            Mode.ToggleAndUndo -> {
                selectionHandler.updateSelection(
                    start,
                    start,
                    !mOriginalSelection!!.contains(start),
                    true
                )
            }

            Mode.FirstItemDependent, Mode.FirstItemDependentToggleAndUndo -> {
                selectionHandler.updateSelection(start, start, !mFirstWasSelected, true)
            }
        }
        if (mStartFinishedListener != null) mStartFinishedListener!!.onSelectionStarted(
            start,
            mFirstWasSelected
        )
    }

    override fun onSelectionFinished(end: Int) {
        mOriginalSelection = null
        if (mStartFinishedListener != null) mStartFinishedListener!!.onSelectionFinished(end)
    }

    override fun onSelectChange(start: Int, end: Int, isSelected: Boolean) {
        when (mMode) {
            Mode.Simple -> {
                if (mCheckSelectionState) checkedUpdateSelection(
                    start,
                    end,
                    isSelected
                ) else selectionHandler.updateSelection(start, end, isSelected, false)
            }

            Mode.ToggleAndUndo -> {
                var i = start
                while (i <= end) {
                    checkedUpdateSelection(i, i, isSelected != mOriginalSelection!!.contains(i))
                    i++
                }
            }

            Mode.FirstItemDependent -> {
                checkedUpdateSelection(start, end, isSelected != mFirstWasSelected)
            }

            Mode.FirstItemDependentToggleAndUndo -> {
                var i = start
                while (i <= end) {
                    checkedUpdateSelection(
                        i,
                        i,
                        if (isSelected) !mFirstWasSelected else mOriginalSelection!!.contains(i)
                    )
                    i++
                }
            }
        }
    }

    private fun checkedUpdateSelection(start: Int, end: Int, newSelectionState: Boolean) {
        if (mCheckSelectionState) {
            for (i in start..end) {
                if (selectionHandler.isSelected(i) != newSelectionState)
                    selectionHandler.updateSelection(i, i, newSelectionState, false)
            }
        } else selectionHandler.updateSelection(start, end, newSelectionState, false)
    }

    interface ISelectionHandler {
        /**
         * @return the currently selected items => can be ignored for [Mode.Simple] and [Mode.FirstItemDependent]
         */
        val selection: Set<Int>?

        /**
         * only used, if [DragSelectionProcessorK.withCheckSelectionState] was enabled
         *
         * @param index the index which selection state wants to be known
         * @return the current selection state of the passed in index
         */
        fun isSelected(index: Int): Boolean

        /**
         * update your adapter and select select/unselect the passed index range, you be get a single for all modes but [Mode.Simple] and [Mode.FirstItemDependent]
         *
         * @param start             the first item of the range who's selection state changed
         * @param end               the last item of the range who's selection state changed
         * @param isSelected        true, if the range should be selected, false otherwise
         * @param calledFromOnStart true, if it was called from the [DragSelectionProcessorK.onSelectionStarted] event
         */
        fun updateSelection(start: Int, end: Int, isSelected: Boolean, calledFromOnStart: Boolean)
    }

    interface ISelectionStartFinishedListener {
        /**
         * @param start                  the item on which the drag selection was started at
         * @param originalSelectionState the original selection state
         */
        fun onSelectionStarted(start: Int, originalSelectionState: Boolean)

        /**
         * @param end the item on which the drag selection was finished at
         */
        fun onSelectionFinished(end: Int)
    }
}