package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.annimon.stream.Stream
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil.set
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentDuplicateMainBinding
import me.devsaki.hentoid.viewholders.DuplicateItem
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import timber.log.Timber

class DuplicateMainFragment : Fragment(R.layout.fragment_duplicate_main) {

    private var _binding: FragmentDuplicateMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<DuplicateViewModel>()
    private val itemAdapter = ItemAdapter<DuplicateItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentDuplicateMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.list.adapter = fastAdapter

        binding.controls.scanFab.setOnClickListener {
            this.onScanClick()
        }

        viewModel.duplicates.observe(viewLifecycleOwner, Observer { l: List<DuplicateViewModel.DuplicateResult>? -> this.onNewDuplicates(l) })
    }

    private fun onScanClick() {
        viewModel.scanForDuplicates(
                binding.controls.useTitle.isChecked,
                binding.controls.useCover.isChecked,
                binding.controls.useArtist.isChecked,
                binding.controls.useSameLanguage.isChecked,
                binding.controls.useSensitivity.selectedIndex
        )
    }

    private fun onNewDuplicates(duplicates: List<DuplicateViewModel.DuplicateResult>?) {
        if (null == duplicates) return

        Timber.i(">> New duplicates ! Size=%s", duplicates.size)

        // TODO update UI title

        val items: List<DuplicateItem> = Stream.of(duplicates).withoutNulls().map { i -> DuplicateItem(i, DuplicateItem.ViewType.MAIN) }.toList()
        set(itemAdapter, items)
    }
}