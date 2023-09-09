package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.select.getSelectExtension
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibrarySplitBinding
import me.devsaki.hentoid.viewholders.IDraggableViewHolder
import me.devsaki.hentoid.viewholders.TextItem
import me.devsaki.hentoid.widget.DragSelectTouchListener
import me.devsaki.hentoid.widget.DragSelectTouchListener.OnDragSelectListener
import me.devsaki.hentoid.widget.DragSelectionProcessor
import me.devsaki.hentoid.widget.DragSelectionProcessor.ISelectionHandler
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper

class SplitDialogFragment : DialogFragment(), ItemTouchCallback {
    companion object {
        private const val KEY_CONTENT = "content"

        operator fun invoke(
            parent: Fragment,
            content: Content
        ) {
            val fragment = SplitDialogFragment()
            val args = Bundle()
            args.putLong(KEY_CONTENT, content.id)
            fragment.arguments = args
            fragment.show(parent.childFragmentManager, null)
        }
    }

    // === UI
    private var binding: DialogLibrarySplitBinding? = null

    private val itemAdapter = ItemAdapter<TextItem<Chapter>>()
    private val fastAdapter: FastAdapter<TextItem<Chapter>> = FastAdapter.with(itemAdapter)
    private val selectExtension = fastAdapter.getSelectExtension()

    private var mDragSelectTouchListener: DragSelectTouchListener? = null

    // === VARIABLES
    private var parent: Parent? = null
    private var contentId: Long = 0
    private var content: Content? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentId = requireArguments().getLong(KEY_CONTENT)
        parent = parentFragment as Parent?
    }

    override fun onDestroy() {
        parent = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogLibrarySplitBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension.let {
            it.isSelectable = true
            it.multiSelect = true
            it.selectOnLongClick = true
            it.selectWithItemUpdate = true
            it.selectionListener = object : ISelectionListener<TextItem<Chapter>> {
                override fun onSelectionChanged(item: TextItem<Chapter>, selected: Boolean) {
                    onSelectionChanged2()
                }
            }

            val helper = FastAdapterPreClickSelectHelper(it)
            fastAdapter.onPreClickListener =
                { _, _, _, position: Int ->
                    helper.onPreClickListener(position)
                }
            fastAdapter.onPreLongClickListener =
                { _, _, _, p: Int ->
                    // Warning : specific code for drag selection
                    mDragSelectTouchListener!!.startDragSelection(p)
                    helper.onPreLongClickListener(p)
                }
        }

        binding?.apply {
            list.adapter = fastAdapter

            // Select on swipe
            val onDragSelectionListener: OnDragSelectListener =
                DragSelectionProcessor(object : ISelectionHandler {
                    override fun getSelection(): Set<Int> {
                        return selectExtension.selections
                    }

                    override fun isSelected(index: Int): Boolean {
                        return selectExtension.selections.contains(index)
                    }

                    override fun updateSelection(
                        start: Int,
                        end: Int,
                        isSelected: Boolean,
                        calledFromOnStart: Boolean
                    ) {
                        if (isSelected) selectExtension.select(IntRange(start, end))
                        else selectExtension.deselect(IntRange(start, end).toMutableList())
                    }
                }).withMode(DragSelectionProcessor.Mode.Simple)
            DragSelectTouchListener().withSelectListener(onDragSelectionListener).let {
                mDragSelectTouchListener = it
                list.addOnItemTouchListener(it)
            }
            nochapterAction.setOnClickListener { onCreateChaptersClick() }
            actionButton.setOnClickListener { onActionClick() }
        }
    }

    override fun onResume() {
        super.onResume()
        val chapterList = loadChapterList()
        itemAdapter.set(chapterList.map { c: Chapter ->
            TextItem(
                c.name,
                c,
                false,
                false,
                false,
                null
            )
        })

        // Display help text is no chapters
        binding?.apply {
            if (chapterList.isEmpty()) {
                nochapterView.visibility = View.VISIBLE
                list.visibility = View.GONE
            } else {
                nochapterView.visibility = View.GONE
                list.visibility = View.VISIBLE
            }
        }
    }

    private fun loadChapterList(): List<Chapter> {
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        try {
            content = dao.selectContent(contentId)
            return if (content != null) content!!.chaptersList else emptyList()
        } finally {
            dao.cleanup()
        }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private fun onSelectionChanged2() {
        val selectedItems: Set<TextItem<Chapter>> = selectExtension.selectedItems
        val selectedCount = selectedItems.size
        binding?.apply {
            actionButton.isEnabled = selectedCount > 0
        }
        if (0 == selectedCount) selectExtension.selectOnLongClick = true
    }

    private fun onCreateChaptersClick() {
        content?.let {
            parent?.readBook(it, true)
        }
    }

    private fun onActionClick() {
        content?.let { c ->
            val chapters = selectExtension.selectedItems.mapNotNull { ti -> ti.getTag() }
            if (chapters.isNotEmpty()) {
                parent?.splitContent(c, chapters)
                dismiss()
            }
        }
    }


    // FastAdapter hooks
    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Update  visuals
        binding?.apply {
            val vh = list.findViewHolderForAdapterPosition(newPosition)
            if (vh is IDraggableViewHolder) {
                (vh as IDraggableViewHolder).onDropped()
            }
        }
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        // Update visuals
        onMove(itemAdapter, oldPosition, newPosition) // change position
        return true
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        // Update visuals
        if (viewHolder is IDraggableViewHolder) {
            (viewHolder as IDraggableViewHolder).onDragged()
        }
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        // Nothing
    }

    interface Parent {
        fun splitContent(content: Content, chapters: List<Chapter>)
        fun readBook(content: Content, forceShowGallery: Boolean)
        fun leaveSelectionMode()
    }
}