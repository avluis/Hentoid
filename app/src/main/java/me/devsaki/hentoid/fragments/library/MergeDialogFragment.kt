package me.devsaki.hentoid.fragments.library

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil.onMove
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryMergeBinding
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.ContentItem
import me.devsaki.hentoid.viewholders.IDraggableViewHolder

class MergeDialogFragment : BaseDialogFragment<MergeDialogFragment.Parent>(), ItemTouchCallback {

    companion object {
        private const val KEY_CONTENTS = "contents"
        private const val KEY_DELETE_DEFAULT = "delete_default"

        operator fun invoke(
            parent: Fragment,
            contentList: List<Content>,
            deleteDefault: Boolean
        ) {
            val args = Bundle()
            args.putLongArray(
                KEY_CONTENTS,
                contentList.map { obj: Content -> obj.id }.toLongArray()
            )
            args.putBoolean(KEY_DELETE_DEFAULT, deleteDefault)
            invoke(parent, MergeDialogFragment(), args)
        }
    }

    // === UI
    private var binding: DialogLibraryMergeBinding? = null

    private val itemAdapter = ItemAdapter<ContentItem>()
    private val fastAdapter: FastAdapter<ContentItem> = FastAdapter.with(itemAdapter)
    private var touchHelper: ItemTouchHelper? = null

    // === VARIABLES
    private lateinit var contentIds: LongArray
    private var deleteDefault = false
    private var initialTitle = ""
    private var sortAsc = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contentIdsVal = requireArguments().getLongArray(KEY_CONTENTS)
        require(contentIdsVal != null && contentIdsVal.isNotEmpty()) { "No content IDs" }
        contentIds = contentIdsVal
        deleteDefault = requireArguments().getBoolean(KEY_DELETE_DEFAULT, false)
    }

    override fun onCancel(dialog: DialogInterface) {
        parent?.leaveSelectionMode()
        super.onCancel(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogLibraryMergeBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val contentList = load()
        val isExternal = contentList[0].status == StatusContent.EXTERNAL

        // Activate drag & drop
        val dragCallback = SimpleDragCallback(this)
        dragCallback.notifyAllDrops = true
        touchHelper = ItemTouchHelper(dragCallback)
        binding?.apply {
            // Toolbar
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_sort -> {
                        load(sortAsc)
                        sortAsc = !sortAsc
                    }
                }
                return@setOnMenuItemClickListener true
            }

            // Recyclerview
            touchHelper?.attachToRecyclerView(list)
            fastAdapter.addEventHook(
                ContentItem.DragHandlerTouchEvent { position ->
                    val vh = list.findViewHolderForAdapterPosition(position)
                    if (vh != null) touchHelper?.startDrag(vh)
                }
            )
            list.adapter = fastAdapter

            if (isExternal) {
                mergeDeleteSwitch.isEnabled = Preferences.isDeleteExternalLibrary()
                mergeDeleteSwitch.isChecked = Preferences.isDeleteExternalLibrary() && deleteDefault
            } else {
                mergeDeleteSwitch.isEnabled = true
                mergeDeleteSwitch.isChecked = deleteDefault
            }
            actionButton.setOnClickListener { onActionClick() }
        }
    }

    private fun load(sortAsc: Boolean? = null): List<Content> {
        var contentList = loadContentList()
        if (contentList.isEmpty()) return emptyList()

        contentList = contentList.sortedWith(ContentHelper.InnerNameNumberContentComparator())
        sortAsc?.let { if (!it) contentList = contentList.reversed() }

        itemAdapter.set(contentList.map { c ->
            ContentItem(c, touchHelper, ContentItem.ViewType.MERGE)
        })

        binding?.titleNew?.editText?.apply {
            if (text.toString() == initialTitle) {
                initialTitle = contentList[0].title
                setText(initialTitle)
            }
        }

        return contentList
    }

    private fun loadContentList(): List<Content> {
        val result: List<Content>
        val dao: CollectionDAO = ObjectBoxDAO(requireContext())
        result = try {
            dao.selectContent(contentIds)
        } finally {
            dao.cleanup()
        }
        return result
    }

    private fun onActionClick() {
        val contents = itemAdapter.adapterItems.mapNotNull { ci -> ci.content }
        var newTitleStr = ""
        binding?.apply {
            titleNew.editText?.apply {
                newTitleStr = text.toString()
            }
            parent?.mergeContents(contents, newTitleStr, mergeDeleteSwitch.isChecked)
        }
        dismissAllowingStateLoss()
    }


    // FastAdapter hooks

    // FastAdapter hooks
    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        // Update visuals
        binding?.apply {
            val vh = list.findViewHolderForAdapterPosition(newPosition)
            if (vh is IDraggableViewHolder) {
                (vh as IDraggableViewHolder).onDropped()
            }
            // Update new title if unedited
            titleNew.editText?.apply {
                if (0 == newPosition && text.toString() == initialTitle) {
                    initialTitle = itemAdapter.getAdapterItem(0).title
                    setText(initialTitle)
                }
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
        fun mergeContents(
            contentList: List<Content>,
            newTitle: String,
            deleteAfterMerging: Boolean
        )

        fun leaveSelectionMode()
    }
}