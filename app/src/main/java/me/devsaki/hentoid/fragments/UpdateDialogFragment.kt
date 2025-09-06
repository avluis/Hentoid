package me.devsaki.hentoid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.runUpdateDownloadWorker
import me.devsaki.hentoid.databinding.DialogUpdateBinding
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.retrofit.GithubServer
import me.devsaki.hentoid.viewholders.GitHubReleaseItem
import me.devsaki.hentoid.workers.UpdateDownloadWorker
import timber.log.Timber

/**
 * "update success" dialog
 */
class UpdateDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(parent: Fragment) {
            invoke(parent, UpdateDialogFragment())
        }

        fun invoke(parent: FragmentActivity) {
            invoke(parent, UpdateDialogFragment())
        }
    }

    // UI
    private var binding: DialogUpdateBinding? = null

    // === VARIABLES
    private val itemAdapter: ItemAdapter<GitHubReleaseItem> = ItemAdapter()

    // 0 = default; 1 = downloading; 2 = downloaded
    private var status = 0

    private var apkUrl = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogUpdateBinding.inflate(inflater, container, false)

        val releaseItemAdapter = FastAdapter.with(itemAdapter)
        binding?.recyclerView?.adapter = releaseItemAdapter

        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        getReleases()

        binding?.apply {
            actionBtn.setOnClickListener { onActionClick() }
        }
    }

    private fun onActionClick() {
        when (status) {
            0 -> downloadUpdate()
            2 -> askInstall()
        }
    }

    private fun downloadUpdate() {
        if (apkUrl.isEmpty()) return
        context?.let { ctx ->
            // Download the latest update (equivalent to tapping the "Update available" notification)
            if (!UpdateDownloadWorker.isRunning(ctx) && apkUrl.isNotEmpty()) {
                Toast.makeText(ctx, R.string.downloading_update, Toast.LENGTH_SHORT).show()
                ctx.runUpdateDownloadWorker(apkUrl)
            }
            // TODO turn pause to cancel
            status = 1
        }
    }

    private fun askInstall() {
        // TODO
    }

    private fun getReleases() {
        lifecycleScope.launch {
            var response: GithubRelease? = null
            withContext(Dispatchers.IO) {
                try {
                    response = GithubServer.api.latestRelease.execute().body()
                } catch (e: Exception) {
                    onCheckError(e)
                }
            }
            response.let {
                if (null == it) Timber.w("Error fetching GitHub latest release data (empty response)")
                else onCheckSuccess(it)
            }
        }
    }

    private fun onCheckSuccess(latestReleaseInfo: GithubRelease) {
        itemAdapter.add(GitHubReleaseItem(latestReleaseInfo))
        apkUrl = latestReleaseInfo.getApkAssetUrl()
    }

    private fun onCheckError(t: Throwable) {
        Timber.w(t, "Error fetching GitHub latest release data")
    }
}