package me.devsaki.hentoid.fragments.about

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.FragmentChangelogBinding
import me.devsaki.hentoid.util.AppHelper
import me.devsaki.hentoid.viewholders.GitHubReleaseItem
import me.devsaki.hentoid.viewmodels.ChangelogViewModel
import me.devsaki.hentoid.workers.UpdateDownloadWorker
import timber.log.Timber
import java.util.*

// TODO - invisible init while loading
class ChangelogFragment : Fragment(R.layout.fragment_changelog) {

    private var _binding: FragmentChangelogBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<ChangelogViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangelogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }

        binding.changelogRecycler.setHasFixedSize(true)

        // TODO - observe update availability through event bus instead of parsing changelog
        viewModel.successValueLive.observe(viewLifecycleOwner) { releasesInfo ->
            val releases: MutableList<GitHubReleaseItem> = ArrayList()
            var latestTagName = ""
            var latestApkUrl = ""
            for (r in releasesInfo) {
                val release = GitHubReleaseItem(r)
                if (release.isTagPrior(BuildConfig.VERSION_NAME)) releases.add(release)
                if (latestTagName.isEmpty()) {
                    latestTagName = release.tagName
                    latestApkUrl = release.apkUrl
                }
            }
            val itemAdapter = ItemAdapter<GitHubReleaseItem>()
            itemAdapter.add(releases)
            binding.changelogRecycler.adapter = FastAdapter.with(itemAdapter)
            if (releasesInfo.size > releases.size) {
                binding.changelogDownloadLatestText.text =
                    getString(R.string.get_latest, latestTagName)
                binding.changelogDownloadLatestText.visibility = View.VISIBLE
                binding.changelogDownloadLatestButton.visibility = View.VISIBLE

                // TODO these 2 should be in a container layout which should be used for click listeners
                binding.changelogDownloadLatestText.setOnClickListener {
                    onDownloadClick(
                        view.context,
                        latestApkUrl
                    )
                }
                binding.changelogDownloadLatestButton.setOnClickListener {
                    onDownloadClick(
                        view.context,
                        latestApkUrl
                    )
                }
            }
            // TODO show RecyclerView
        }

        viewModel.errorValueLive.observe(viewLifecycleOwner) { t ->
            Timber.w(t, "Error fetching GitHub releases data")
            // TODO - don't show recyclerView; show an error message on the entire screen
        }
    }

    private fun onDownloadClick(context: Context, apkUrl: String) {
        // Download the latest update (equivalent to tapping the "Update available" notification)
        if (!UpdateDownloadWorker.isRunning(context) && apkUrl.isNotEmpty()) {
            Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show()
            AppHelper.runUpdateDownloadWorker(context, apkUrl)
        }
    }
}