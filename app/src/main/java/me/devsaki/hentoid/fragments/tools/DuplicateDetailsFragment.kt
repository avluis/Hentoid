package me.devsaki.hentoid.fragments.tools

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.databinding.FragmentDuplicateDetailsBinding
import me.devsaki.hentoid.events.CommunicationEvent
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

class DuplicateDetailsFragment : Fragment(R.layout.fragment_duplicate_details) {

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


    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is DuplicateDetectorActivity) { "Parent activity has to be a DuplicateDetectorActivity" }
        activity = WeakReference<DuplicateDetectorActivity>(requireActivity() as DuplicateDetectorActivity)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDuplicateDetailsBinding.inflate(inflater, container, false)
        addCustomBackControl()
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

        binding.list.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        FastScrollerBuilder(binding.list).build()
        binding.list.adapter = fastAdapter

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[DuplicateViewModel::class.java]
        viewModel.selectedDuplicates.observe(viewLifecycleOwner, { l: List<DuplicateDetectorWorker.DuplicateResult>? -> this.onDuplicatesChanged(l) })
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

    @Synchronized
    private fun onDuplicatesChanged(duplicates: List<DuplicateDetectorWorker.DuplicateResult>?) {
        if (null == duplicates) return

        Timber.i(">> New selected duplicates ! Size=%s", duplicates.size)

        // TODO update UI title

        // Order by relevance desc and transforms to DuplicateItem
        val items = duplicates.sortedByDescending { it.calcTotalScore() }.map { DuplicateItem(it, DuplicateItem.ViewType.DETAILS) }.toMutableList()
        // Add the reference item on top
        if (items.isNotEmpty()) items.add(0, DuplicateItem(duplicates[0].reference, DuplicateItem.ViewType.DETAILS))
        FastAdapterDiffUtil[itemAdapter] = items
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onActivityEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.RC_DUPLICATE_DETAILS) return
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