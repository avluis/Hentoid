package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
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
import me.devsaki.hentoid.util.DuplicateHelper.Companion.STEP_COVER_INDEX
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.DuplicateItem
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
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

    // VARS
    private var enabled = true

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is DuplicateDetectorActivity) { "Parent activity has to be a DuplicateDetectorActivity" }
        activity = WeakReference<DuplicateDetectorActivity>(requireActivity() as DuplicateDetectorActivity)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDuplicateMainBinding.inflate(inflater, container, false)
        addCustomBackControl()
        activity.get()?.initFragmentToolbars(this::toolbarOnItemClicked)
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

        binding.list.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        FastScrollerBuilder(binding.list).build()
        binding.list.adapter = fastAdapter

        // Item click listener
        // TODO it's actually on the "X duplicates" button...
        fastAdapter.onClickListener = { _: View?, _: IAdapter<DuplicateItem>, i: DuplicateItem, _: Int -> onItemClick(i) }

        binding.controls.scanFab.setOnClickListener {
            this.onScanClick()
        }

        binding.controls.useTitle.isChecked = Preferences.isDuplicateUseTitle()
        binding.controls.useCover.isChecked = Preferences.isDuplicateUseCover()
        binding.controls.useArtist.isChecked = Preferences.isDuplicateUseArtist()
        binding.controls.useSameLanguage.isChecked = Preferences.isDuplicateUseSameLanguage()
        binding.controls.useSensitivity.setItems(R.array.duplicate_use_sensitivities)
        binding.controls.useSensitivity.selectItemByIndex(Preferences.getDuplicateSensitivity())

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[DuplicateViewModel::class.java]
        viewModel.allDuplicates.observe(viewLifecycleOwner, { this.onDuplicatesChanged(it) })
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
        requireActivity().onBackPressed()
    }

    private fun toolbarOnItemClicked(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_settings -> setSettingsPanelVisibility()
        }
        return true
    }

    private fun setSettingsPanelVisibility(visibility: Boolean? = null) {
        var result = View.VISIBLE
        if (null == visibility) { // Toggle
            if (View.VISIBLE == binding.controls.root.visibility) result = View.GONE
        } else { // Force visibility
            if (!visibility) result = View.GONE
        }

        if (result == View.VISIBLE) {
            binding.controls.scanFab.visibility = View.VISIBLE
            binding.controls.detectBooksTxt.visibility = View.GONE
            binding.controls.detectBooksPb.visibility = View.GONE
            binding.controls.indexPicturesTxt.visibility = View.GONE
            binding.controls.indexPicturesPb.visibility = View.GONE
        }
        binding.controls.root.visibility = result
    }

    private fun onScanClick() {
        Preferences.setDuplicateUseTitle(binding.controls.useTitle.isChecked)
        Preferences.setDuplicateUseCover(binding.controls.useCover.isChecked)
        Preferences.setDuplicateUseArtist(binding.controls.useArtist.isChecked)
        Preferences.setDuplicateUseSameLanguage(binding.controls.useSameLanguage.isChecked)
        Preferences.setDuplicateSensitivity(binding.controls.useSensitivity.selectedIndex)

        binding.controls.scanFab.visibility = View.GONE
        if (binding.controls.useCover.isChecked) {
            binding.controls.indexPicturesTxt.visibility = View.VISIBLE
            binding.controls.detectBooksPb.progress = 0
            binding.controls.indexPicturesPb.visibility = View.VISIBLE
        } else {
            binding.controls.detectBooksTxt.visibility = View.VISIBLE
            binding.controls.detectBooksPb.progress = 0
            binding.controls.detectBooksPb.visibility = View.VISIBLE
        }

        activity.get()?.firstUse = false
        binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_processing)

        viewModel.scanForDuplicates(
                binding.controls.useTitle.isChecked,
                binding.controls.useCover.isChecked,
                binding.controls.useArtist.isChecked,
                binding.controls.useSameLanguage.isChecked,
                binding.controls.useSensitivity.selectedIndex
        )
    }

    @Synchronized
    private fun onDuplicatesChanged(duplicates: List<DuplicateEntry>) {
//        if (null == duplicates) return

        Timber.i(">> New duplicates ! Size=%s", duplicates.size)

        // Update settings panel visibility
        if (duplicates.isEmpty()) {
            if (activity.get() != null && activity.get()!!.firstUse) {
                setSettingsPanelVisibility(duplicates.isEmpty())
                binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_first_use)
            } else if (DuplicateDetectorWorker.isRunning()) {
                binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_processing)
            } else {
                binding.emptyTxt.text = context?.getText(R.string.duplicate_empty_no_result)
            }
        } else {
            setSettingsPanelVisibility(false)
        }

        // TODO update UI title

        // Group by reference book and count duplicates
        val entries: MutableList<DuplicateEntry> = ArrayList()
        val map = duplicates.groupBy { it.referenceContent }.mapValues { it.value.sumBy { 1 } }.toMap()
        for (mapEntry in map) {
            if (mapEntry.key != null) {
                val entry = DuplicateEntry(mapEntry.key!!.id, mapEntry.key!!.size)
                entry.referenceContent = mapEntry.key!!
                entries.add(entry)
            }
        }
        // Transform to DuplicateItem
        val items = entries.map { DuplicateItem(it, DuplicateItem.ViewType.MAIN) }
        set(itemAdapter, items)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        val progressBar: ProgressBar = if (STEP_COVER_INDEX == event.step) binding.controls.indexPicturesPb else binding.controls.detectBooksPb
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            progressBar.visibility = View.VISIBLE
            progressBar.max = event.elementsTotal
            progressBar.progress = event.elementsOK + event.elementsKO
            if (STEP_COVER_INDEX == event.step) {
                binding.controls.indexPicturesTxt.visibility = View.VISIBLE
            } else {
                binding.controls.detectBooksTxt.visibility = View.VISIBLE
            }
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            if (STEP_COVER_INDEX == event.step) {
                binding.controls.detectBooksTxt.visibility = View.VISIBLE
                binding.controls.detectBooksPb.progress = 0
                binding.controls.detectBooksPb.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Callback for the group holder itself
     *
     * @param item GroupDisplayItem that has been clicked on
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

    private fun onEnable() {
        enabled = true
        callback?.isEnabled = true
    }

    private fun onDisable() {
        enabled = false
        callback?.isEnabled = false
    }
}