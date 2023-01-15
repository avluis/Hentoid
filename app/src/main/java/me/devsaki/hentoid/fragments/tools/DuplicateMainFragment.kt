package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.databinding.FragmentDuplicateMainBinding
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.events.ServiceDestroyedEvent
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.viewholders.DuplicateItem
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import me.devsaki.hentoid.workers.DuplicateDetectorWorker.STEP_DUPLICATES
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.ref.WeakReference

class DuplicateMainFragment : Fragment(R.layout.fragment_duplicate_main) {

    private var _binding: FragmentDuplicateMainBinding? = null
    private val binding get() = _binding!!

    // Communication
    private var callback: OnBackPressedCallback? = null
    private lateinit var activity: WeakReference<DuplicateDetectorActivity>
    lateinit var viewModel: DuplicateViewModel

    // UI
    private val itemAdapter = ItemAdapter<DuplicateItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)
    private lateinit var topPanel: DuplicateMainTopPanel

    // VARS
    private var enabled = true
    private var firstUse = true

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
        _binding = FragmentDuplicateMainBinding.inflate(inflater, container, false)
        addCustomBackControl()
        activity.get()?.initFragmentToolbars(this::onToolbarItemClicked)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onStop()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.list.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        FastScrollerBuilder(binding.list).build()
        binding.list.adapter = fastAdapter

        // Item click listener
        // TODO it's actually on the "X duplicates" button...
        fastAdapter.onClickListener =
            { _: View?, _: IAdapter<DuplicateItem>, i: DuplicateItem, _: Int -> onItemClick(i) }

        topPanel = DuplicateMainTopPanel(activity.get()!!)

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[DuplicateViewModel::class.java]
        viewModel.allDuplicates.observe(viewLifecycleOwner) { this.onDuplicatesChanged(it) }
        viewModel.firstUse.observe(viewLifecycleOwner) { b -> firstUse = b }
    }

    private fun addCustomBackControl() {
        callback?.remove()
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onCustomBackPress()
            }
        }
        activity.get()?.onBackPressedDispatcher?.addCallback(activity.get()!!, callback!!)
    }

    private fun onCustomBackPress() {
        callback?.remove()
        requireActivity().finish()
    }

    private fun onToolbarItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_settings -> topPanel.showAsDropDown(activity.get()!!.getToolbarView())
        }
        return true
    }

    @Synchronized
    private fun onDuplicatesChanged(duplicates: List<DuplicateEntry>) {
        Timber.i(">> New duplicates ! Size=%s", duplicates.size)

        // Update settings panel visibility
        if (duplicates.isEmpty()) {
            binding.emptyTxt.visibility = View.VISIBLE
            when {
                firstUse -> {
                    binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_first_use)
                }
                DuplicateDetectorWorker.isRunning(requireContext()) -> {
                    binding.emptyTxt.text = context?.getText(R.string.duplicate_processing)
                }
                else -> {
                    binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_no_result)
                }
            }
        } else {
            binding.emptyTxt.visibility = View.GONE
        }

        // TODO update UI title

        // Group by reference book and count duplicates
        val entries: MutableList<DuplicateEntry> = ArrayList()
        // TODO use groupingBy + eachCount
        val map =
            duplicates.groupBy { it.referenceContent }.mapValues { it.value.sumOf { 1L } }
                .toMap()
        for (mapEntry in map) {
            if (mapEntry.key != null) {
                val entry = DuplicateEntry(mapEntry.key!!.id, mapEntry.key!!.size)
                entry.referenceContent = mapEntry.key!!
                entry.nbDuplicates = mapEntry.value.toInt()
                entries.add(entry)
            }
        }
        // Transform to DuplicateItem
        val items = entries.map { DuplicateItem(it, DuplicateItem.ViewType.MAIN) }
        set(itemAdapter, items)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        if (event.processId != R.id.duplicate_index && event.processId != R.id.duplicate_detect) return

        topPanel.onProcessEvent(event)

        if (ProcessEvent.EventType.COMPLETE == event.eventType && STEP_DUPLICATES == event.step) {
            topPanel.dismiss()
            ToastHelper.toast(requireContext(), R.string.duplicate_notif_complete_title)
        } else if (topPanel.isVisible() && DuplicateDetectorWorker.isRunning(
                requireContext()
            )
        ) {
            binding.emptyTxt.text = context?.getText(R.string.duplicate_processing)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ServiceDestroyedEvent) {
        if (event.service != R.id.duplicate_detector_service) return
        binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_first_use)
    }

    /**
     * Callback for duplicate item click
     *
     * @param item DuplicateItem that has been clicked on
     */
    private fun onItemClick(item: DuplicateItem): Boolean {
        if (item.content != null) {
            activity.get()?.showDetailsFor(item.content!!)
        }
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onActivityEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.RC_DUPLICATE_MAIN) return
        when (event.type) {
            CommunicationEvent.EV_ENABLE -> onEnable()
            CommunicationEvent.EV_DISABLE -> onDisable()
            else -> {
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceDestroyedEvent(event: ServiceDestroyedEvent) {
        if (event.service == R.id.duplicate_detector_service) {
            // TODO find a way to display the "try again" message when the service doesn't stop normally
            topPanel.dismiss()
            topPanel.onServiceDestroyedEvent()
            if (0 == itemAdapter.adapterItemCount)
                binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_no_result)
        }
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
}