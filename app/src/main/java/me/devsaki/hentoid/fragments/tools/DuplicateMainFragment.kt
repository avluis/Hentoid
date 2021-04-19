package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.annimon.stream.Stream
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentDuplicateMainBinding
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.DuplicateItem
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber

class DuplicateMainFragment : Fragment(R.layout.fragment_duplicate_main) {

    private var _binding: FragmentDuplicateMainBinding? = null
    private val binding get() = _binding!!

    lateinit var viewModel: DuplicateViewModel
    private val itemAdapter = ItemAdapter<DuplicateItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDuplicateMainBinding.inflate(inflater, container, false)
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

        binding.controls.scanFab.setOnClickListener {
            this.onScanClick()
        }

        binding.controls.useTitle.isChecked = Preferences.isDuplicateUseTitle()
        binding.controls.useCover.isChecked = Preferences.isDuplicateUseCover()
        binding.controls.useArtist.isChecked = Preferences.isDuplicateUseArtist()
        binding.controls.useSameLanguage.isChecked = Preferences.isDuplicateUseSameLanguage()
        binding.controls.useSensitivity.setItems(R.array.duplicate_use_sensitivities)
        binding.controls.useSensitivity.selectItemByIndex(Preferences.getDuplicateSensitivity())

        // TODO only do that when dupes DB is empty
        binding.controls.root.visibility = View.VISIBLE

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[DuplicateViewModel::class.java]
        viewModel.duplicates.observe(viewLifecycleOwner, { l: List<DuplicateViewModel.DuplicateResult>? -> this.onNewDuplicates(l) })
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

        viewModel.scanForDuplicates(
                binding.controls.useTitle.isChecked,
                binding.controls.useCover.isChecked,
                binding.controls.useArtist.isChecked,
                binding.controls.useSameLanguage.isChecked,
                binding.controls.useSensitivity.selectedIndex
        )
    }

    @Synchronized
    private fun onNewDuplicates(duplicates: List<DuplicateViewModel.DuplicateResult>?) {
        if (null == duplicates) return

        Timber.i(">> New duplicates ! Size=%s", duplicates.size)

        // TODO update UI title

        val items: List<DuplicateItem> = Stream.of(duplicates).withoutNulls().map { i -> DuplicateItem(i, DuplicateItem.ViewType.MAIN) }.toList()
        set(itemAdapter, items)

        binding.controls.root.visibility = View.GONE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        val progressBar: ProgressBar = if (DuplicateViewModel.STEP_COVER_INDEX == event.step) binding.controls.indexPicturesPb else binding.controls.detectBooksPb
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            progressBar.max = event.elementsTotal
            progressBar.progress = event.elementsOK + event.elementsKO
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            if (DuplicateViewModel.STEP_COVER_INDEX == event.step) {
                binding.controls.detectBooksTxt.visibility = View.VISIBLE
                binding.controls.detectBooksPb.progress = 0
                binding.controls.detectBooksPb.visibility = View.VISIBLE
            }
        }
    }
}