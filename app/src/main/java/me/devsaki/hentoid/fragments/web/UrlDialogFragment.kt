package me.devsaki.hentoid.fragments.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.databinding.DialogWebUrlBinding
import me.devsaki.hentoid.util.Helper

private const val URL = "URL"

/**
 * Dialog for URL operations
 */
class UrlDialogFragment() : DialogFragment() {

    constructor(url: String) : this() {
        arguments = bundleOf(URL to url)
    }

    private var binding: DialogWebUrlBinding? = null

    private var url = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkNotNull(arguments) { "No arguments found" }
        url = requireArguments().getString(URL, "")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogWebUrlBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        binding?.let {
            it.url.text = url
            it.externalBrowserBtn.setOnClickListener { requireActivity().startBrowserActivity(url) }
            it.shareBtn.setOnClickListener { Helper.shareText(requireContext(), "", url) }
        }
    }
}