package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentChangelogBinding
import me.devsaki.hentoid.databinding.FragmentDuplicateDetailsBinding
import me.devsaki.hentoid.viewmodels.DuplicateViewModel

class DuplicateDetailsFragment : Fragment(R.layout.fragment_duplicate_details) {

    private var _binding: FragmentDuplicateDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<DuplicateViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentDuplicateDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // TODO
    }
}