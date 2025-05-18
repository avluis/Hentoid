package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.ISelectionListener
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.select.getSelectExtension
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibrarySplitBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.viewholders.ContentItem
import me.devsaki.hentoid.widget.DragSelectTouchListener
import me.devsaki.hentoid.widget.DragSelectTouchListener.OnDragSelectListener
import me.devsaki.hentoid.widget.DragSelectionProcessor
import me.devsaki.hentoid.widget.DragSelectionProcessor.ISelectionHandler
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper

class SplitDialogFragment : BaseDialogFragment<SplitDialogFragment.Parent>() {
    companion object {
        private const val KEY_CONTENT = "content"

        operator fun invoke(parent: Fragment, content: Content) {
            val args = Bundle()
            args.putLong(KEY_CONTENT, content.id)
            invoke(parent, SplitDialogFragment(), args)
        }
    }

    // === UI
    private var binding: DialogLibrarySplitBinding? = null

    private val itemAdapter = ItemAdapter<ContentItem>()
    private val fastAdapter: FastAdapter<ContentItem> = FastAdapter.with(itemAdapter)
    private val selectExtension = fastAdapter.getSelectExtension()

    private var mDragSelectTouchListener: DragSelectTouchListener? = null

    // === VARIABLES
    private var contentId: Long = 0
    private var content: Content? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentId = requireArguments().getLong(KEY_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogLibrarySplitBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension.apply {
            isSelectable = true
            multiSelect = true
            selectOnLongClick = true
            selectWithItemUpdate = true
            selectionListener = object : ISelectionListener<ContentItem> {
                override fun onSelectionChanged(item: ContentItem, selected: Boolean) {
                    onSelectionChanged2()
                }
            }
        }

        val helper = FastAdapterPreClickSelectHelper(fastAdapter, selectExtension)
        fastAdapter.onPreClickListener =
            { _, _, _, position: Int -> helper.onPreClickListener(position) }
        fastAdapter.onPreLongClickListener =
            { _, _, _, p: Int ->
                // Warning : specific code for drag selection
                mDragSelectTouchListener!!.startDragSelection(p)
                helper.onPreLongClickListener(p)
            }

        binding?.apply {
            list.adapter = fastAdapter

            // Select / deselect on swipe
            val onDragSelectionListener: OnDragSelectListener =
                DragSelectionProcessor(object : ISelectionHandler {
                    override val selection: Set<Int>
                        get() = selectExtension.selections

                    override fun isSelected(index: Int): Boolean {
                        return selectExtension.selections.contains(index)
                    }

                    override fun updateSelection(
                        start: Int,
                        end: Int,
                        isSelected: Boolean,
                        calledFromOnStart: Boolean
                    ) {
                        if (isSelected) IntRange(start, end).forEach { selectExtension.select(it, false, true) }
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
        itemAdapter.set(chapterList.map { c -> ContentItem(c) })

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
        val dao: CollectionDAO = ObjectBoxDAO()
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
        val selectedItems: Set<ContentItem> = selectExtension.selectedItems
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
            val chapters = selectExtension.selectedItems.mapNotNull { it.chapter }
            if (chapters.isNotEmpty()) {
                val deleteAfter = binding?.splitDeleteSwitch?.isChecked ?: false
                parent?.splitContent(c, chapters, deleteAfter)
            }
        }
        dismiss()
    }

    interface Parent {
        fun splitContent(content: Content, chapters: List<Chapter>, deleteAfter: Boolean)
        fun readBook(content: Content, forceShowGallery: Boolean)
        fun leaveSelectionMode()
    }
}