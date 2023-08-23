package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.annimon.stream.Stream
import com.google.android.material.materialswitch.MaterialSwitch
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.DiffCallback
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import com.mikepenz.fastadapter.listeners.ClickEventHook
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.activities.bundles.DuplicateItemBundle
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.databinding.FragmentDuplicateDetailsBinding
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.fragments.library.MergeDialogFragment
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.viewholders.DuplicateItem
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.ref.WeakReference

@Suppress("PrivatePropertyName")
class DuplicateDetailsFragment : Fragment(R.layout.fragment_duplicate_details),
    MergeDialogFragment.Parent {

    private var _binding: FragmentDuplicateDetailsBinding? = null
    private val binding get() = _binding!!

    // Communication
    private var callback: OnBackPressedCallback? = null
    private lateinit var activity: WeakReference<DuplicateDetectorActivity>
    lateinit var viewModel: DuplicateViewModel

    // UI
    private val itemAdapter = ItemAdapter<DuplicateItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    // Vars
    private var enabled = true


    private val ITEM_DIFF_CALLBACK: DiffCallback<DuplicateItem> =
        object : DiffCallback<DuplicateItem> {
            override fun areItemsTheSame(oldItem: DuplicateItem, newItem: DuplicateItem): Boolean {
                return oldItem.identifier == newItem.identifier
            }

            override fun areContentsTheSame(
                oldItem: DuplicateItem,
                newItem: DuplicateItem
            ): Boolean {
                return (oldItem.keep == newItem.keep)
                        && (oldItem.isBeingDeleted == newItem.isBeingDeleted)
            }

            override fun getChangePayload(
                oldItem: DuplicateItem,
                oldItemPosition: Int,
                newItem: DuplicateItem,
                newItemPosition: Int
            ): Any? {
                val diffBundleBuilder = DuplicateItemBundle()
                if (oldItem.keep != newItem.keep) {
                    diffBundleBuilder.isKeep = newItem.keep
                }
                if (oldItem.isBeingDeleted != newItem.isBeingDeleted) {
                    diffBundleBuilder.isBeingDeleted = newItem.isBeingDeleted
                }
                return if (diffBundleBuilder.isEmpty) null else diffBundleBuilder.bundle
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is DuplicateDetectorActivity) { "Parent activity has to be a DuplicateDetectorActivity" }
        activity =
            WeakReference<DuplicateDetectorActivity>(requireActivity() as DuplicateDetectorActivity)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDuplicateDetailsBinding.inflate(inflater, container, false)
        addCustomBackControl()
        activity.get()?.initFragmentToolbars(this::onToolbarItemClicked)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[DuplicateViewModel::class.java]

        // List
        binding.list.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        FastScrollerBuilder(binding.list).build()
        binding.list.adapter = fastAdapter

        viewModel.selectedDuplicates.observe(
            viewLifecycleOwner
        ) { l: List<DuplicateEntry>? -> this.onDuplicatesChanged(l) }

        // Item click listener
        fastAdapter.onClickListener = { _, _, item, _ ->
            onItemClick(item)
            false
        }

        // Site button click listener
        fastAdapter.addEventHook(object : ClickEventHook<DuplicateItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<DuplicateItem>,
                item: DuplicateItem
            ) {
                val c = item.content
                if (c != null) ContentHelper.viewContentGalleryPage(requireContext(), c)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is DuplicateItem.ContentViewHolder) {
                    viewHolder.siteButton
                } else super.onBind(viewHolder)
            }
        })

        // "Keep/delete" switch click listener
        fastAdapter.addEventHook(object : ClickEventHook<DuplicateItem>() {
            override fun onClick(
                v: View,
                position: Int,
                fastAdapter: FastAdapter<DuplicateItem>,
                item: DuplicateItem
            ) {
                onBookChoice(item.content, (v as MaterialSwitch).isChecked)
            }

            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return if (viewHolder is DuplicateItem.ContentViewHolder) {
                    viewHolder.keepDeleteSwitch
                } else super.onBind(viewHolder)
            }
        })

        binding.applyBtn.setOnClickListener {
            binding.applyBtn.isEnabled = false
            viewModel.applyChoices {
                binding.applyBtn.isEnabled = true
                activity.get()?.goBackToMain()
            }
        }
    }

    private fun addCustomBackControl() {
        if (callback != null) callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onCustomBackPress()
            }
        }
        activity.get()!!.onBackPressedDispatcher.addCallback(activity.get()!!, callback!!)
    }

    private fun onCustomBackPress() {
        Handler(Looper.getMainLooper()).postDelayed({ activity.get()?.goBackToMain() }, 100)
    }

    private fun onItemClick(item: DuplicateItem) {
        val c: Content? = item.content
        // Process the click
        if (null == c) {
            ToastHelper.toast(R.string.err_no_content)
            return
        }

        if (!ContentHelper.openReader(requireContext(), c, -1, null, false, true))
            ToastHelper.toast(R.string.err_no_content)
    }

    private fun onBookChoice(item: Content?, choice: Boolean) {
        if (item != null)
            viewModel.setBookChoice(item, choice)
    }

    @Synchronized
    private fun onDuplicatesChanged(duplicates: List<DuplicateEntry>?) {
        if (null == duplicates) return

        Timber.i(">> New selected duplicates ! Size=%s", duplicates.size)

        // TODO update UI title

        activity.get()?.updateTitle(duplicates.size)
        val externalCount =
            duplicates.asSequence().map(DuplicateEntry::duplicateContent)
                .filterNotNull()
                .map { c -> c.status }
                .filter { s -> s.equals(StatusContent.EXTERNAL) }.count()
        val streamedCount = duplicates.asSequence().map(DuplicateEntry::duplicateContent)
            .filterNotNull()
            .map { c -> c.downloadMode }
            .filter { mode -> mode == Content.DownloadMode.STREAM }.count()
        val localCount = duplicates.size - externalCount - streamedCount

        // streamed, external
        activity.get()?.updateToolbar(localCount, externalCount, streamedCount)

        // Order by relevance desc and transforms to DuplicateItem
        val items = duplicates.sortedByDescending { it.calcTotalScore() }
            .map { DuplicateItem(it, DuplicateItem.ViewType.DETAILS) }.toMutableList()
        set(itemAdapter, items, ITEM_DIFF_CALLBACK)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onActivityEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.RC_DUPLICATE_DETAILS) return
        when (event.type) {
            CommunicationEvent.EV_ENABLE -> onEnable()
            CommunicationEvent.EV_DISABLE -> onDisable()
        }
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_merge -> {
                MergeDialogFragment.invoke(
                    this,
                    Stream.of(itemAdapter.adapterItems)
                        .map<Content> { obj: DuplicateItem -> obj.content }
                        .toList(), true
                )
            }
        }
        return true
    }

    private fun onEnable() {
        enabled = true
        activity.get()?.initFragmentToolbars(this::onToolbarItemClicked)
        callback?.isEnabled = true
    }

    private fun onDisable() {
        enabled = false
        callback?.isEnabled = false
    }

    override fun mergeContents(
        contentList: List<Content>,
        newTitle: String,
        deleteAfterMerging: Boolean
    ) {
        viewModel.mergeContents(
            contentList,
            newTitle,
            deleteAfterMerging,
        ) {
            ToastHelper.toast(R.string.merge_success)
            activity.get()?.goBackToMain()
        }
        ProgressDialogFragment.invoke(
            parentFragmentManager,
            resources.getString(R.string.merge_progress),
            R.plurals.page
        )
    }

    override fun leaveSelectionMode() {
        // Not applicable to this screen
    }
}