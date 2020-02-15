package me.devsaki.hentoid.fragments.about

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.android.synthetic.main.fragment_changelog.*
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.services.UpdateCheckService
import me.devsaki.hentoid.services.UpdateDownloadService
import me.devsaki.hentoid.viewholders.GitHubReleaseItem
import me.devsaki.hentoid.viewmodels.ChangelogViewModel
import timber.log.Timber
import java.util.*

// TODO - invisible init while loading
class ChangelogFragment : Fragment(R.layout.fragment_changelog) {

    private val viewModel by viewModels<ChangelogViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }

        // TODO these 2 should be in a container layout which should be used for click listeners
        changelogDownloadLatestText.setOnClickListener { onDownloadClick() }
        changelogDownloadLatestButton.setOnClickListener { onDownloadClick() }

        changelogRecycler.setHasFixedSize(true)

        // TODO - observe update availability through event bus instead of parsing changelog
        viewModel.successValueLive.observe(viewLifecycleOwner) { releasesInfo ->
            val releases: MutableList<GitHubReleaseItem> = ArrayList()
            var latestTagName = ""
            for (r in releasesInfo) {
                val release = GitHubReleaseItem(r)
                if (release.isTagPrior(BuildConfig.VERSION_NAME)) releases.add(release)
                if (latestTagName.isEmpty()) latestTagName = release.tagName
            }
            val itemAdapter = ItemAdapter<GitHubReleaseItem>()
            itemAdapter.add(releases)
            changelogRecycler.adapter = FastAdapter.with(itemAdapter)
            if (releasesInfo.size > releases.size) {
                changelogDownloadLatestText.text = getString(R.string.get_latest, latestTagName)
                changelogDownloadLatestText.visibility = View.VISIBLE
                changelogDownloadLatestButton.visibility = View.VISIBLE
            }
            // TODO show RecyclerView
        }

        viewModel.errorValueLive.observe(viewLifecycleOwner) { t ->
            Timber.w(t, "Error fetching GitHub releases data")
            // TODO - don't show recyclerView; show an error message on the entire screen
        }
    }

    private fun onDownloadClick() {
        // Equivalent to "check for updates" preferences menu
        if (!UpdateDownloadService.isRunning()) {
            val intent = UpdateCheckService.makeIntent(requireContext(), true)
            requireContext().startService(intent)
        }
    }
}