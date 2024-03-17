package me.devsaki.hentoid.fragments.web

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.databinding.DialogWebUrlBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Helper

/**
 * Dialog for URL operations
 */
class UrlDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        private const val URL = "URL"

        operator fun invoke(parent: FragmentActivity, url: String) {
            val args = Bundle()
            args.putString(URL, url)
            invoke(parent, UrlDialogFragment(), args)
        }
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