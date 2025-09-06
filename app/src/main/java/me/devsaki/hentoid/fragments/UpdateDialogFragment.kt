package me.devsaki.hentoid.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
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
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.json.GithubRelease
import me.devsaki.hentoid.retrofit.GithubServer
import me.devsaki.hentoid.viewholders.GitHubReleaseItem
import me.devsaki.hentoid.workers.APK_MIMETYPE
import me.devsaki.hentoid.workers.UpdateDownloadWorker
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import kotlin.math.roundToInt

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

    // 0 = default; 1 = downloading APK; 2 = APK downloaded
    private var status = 0
    private var apkRemoteUrl = ""
    private var apkFileUri = Uri.EMPTY


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogUpdateBinding.inflate(inflater, container, false)

        val releaseItemAdapter = FastAdapter.with(itemAdapter)
        binding?.recyclerView?.adapter = releaseItemAdapter

        EventBus.getDefault().register(this)

        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        EventBus.getDefault().unregister(this)
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
        Timber.d("status=$status")
        when (status) {
            0 -> downloadUpdate()
            1 -> cancelUpdate()
            2 -> askInstall()
        }
    }

    private fun downloadUpdate() {
        if (apkRemoteUrl.isEmpty()) return
        context?.let { ctx ->
            // Download the latest update (equivalent to tapping the "Update available" notification)
            if (!UpdateDownloadWorker.isRunning(ctx) && apkRemoteUrl.isNotEmpty()) {
                Toast.makeText(ctx, R.string.downloading_update, Toast.LENGTH_SHORT).show()
                ctx.runUpdateDownloadWorker(apkRemoteUrl)
            }
            binding?.actionBtn?.setIconResource(R.drawable.ic_action_pause)
            status = 1
        }
    }

    private fun cancelUpdate() {
        EventBus.getDefault().post(
            CommunicationEvent(
                CommunicationEvent.Type.CANCEL,
                CommunicationEvent.Recipient.UPDATE_WORKER
            )
        )
        binding?.progress?.visibility = View.INVISIBLE
    }

    private fun askInstall() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(apkFileUri, APK_MIMETYPE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivity(intent)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        if (event.processId != R.id.update_download_service) return
        binding?.progress?.apply {
            min = 0
            max = 100
            progress = event.progressPc.roundToInt()
            isVisible = true
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onCommunicationEvent(event: CommunicationEvent) {
        if (event.type != CommunicationEvent.Type.APK_AVAILABLE) return
        binding?.progress?.visibility = View.INVISIBLE
        binding?.actionBtn?.setIconResource(R.drawable.ic_app_update)
        apkFileUri = event.message.toUri()
        status = 2
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
        apkRemoteUrl = latestReleaseInfo.getApkAssetUrl()
    }

    private fun onCheckError(t: Throwable) {
        Timber.w(t, "Error fetching GitHub latest release data")
    }
}