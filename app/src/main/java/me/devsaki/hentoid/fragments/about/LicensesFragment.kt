package me.devsaki.hentoid.fragments.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentLicensesBinding

class LicensesFragment : Fragment(R.layout.fragment_licenses) {

    private var _binding: FragmentLicensesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentLicensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.licensesToolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }
        binding.licensesWebView.loadUrl("file:///android_asset/licenses.html")
    }
}