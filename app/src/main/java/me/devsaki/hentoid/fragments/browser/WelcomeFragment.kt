package me.devsaki.hentoid.fragments.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.sources.CustomWebViewClient
import me.devsaki.hentoid.database.domains.SiteHistory
import me.devsaki.hentoid.databinding.FragmentWebWelcomeBinding
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.viewmodels.BrowserViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class WelcomeFragment : Fragment(R.layout.fragment_web_welcome) {

    // == COMMUNICATION
    // Viewmodel
    private lateinit var viewModel: BrowserViewModel

    // === UI
    private var binding: FragmentWebWelcomeBinding? = null

    // === VARIABLES
    private var parent: CustomWebViewClient.BrowserActivity? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        parent = null
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWebWelcomeBinding.inflate(inflater, container, false)

        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelFactory(requireActivity().application)
        )[BrowserViewModel::class.java]

        viewModel.siteHistory().observe(viewLifecycleOwner) { onHistoryChanged(it) }

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parent = activity as CustomWebViewClient.BrowserActivity?

        viewModel.loadHistory()
    }

    private fun onHistoryChanged(history: List<SiteHistory>) {

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.recipient != CommunicationEvent.Recipient.DRAWER) return
        if (CommunicationEvent.Type.CLOSED == event.type) viewModel.updateBookmarksJson()
    }
}