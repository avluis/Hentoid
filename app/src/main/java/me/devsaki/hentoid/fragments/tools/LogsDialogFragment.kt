package me.devsaki.hentoid.fragments.tools

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.DialogToolsAppLogsBinding
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.file.openFile
import me.devsaki.hentoid.util.file.shareFile
import me.devsaki.hentoid.util.formatEpochToDate
import me.devsaki.hentoid.viewholders.TextItem
import timber.log.Timber
import java.time.format.DateTimeFormatter
import java.util.Locale

class LogsDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(fragment: Fragment) {
            invoke(fragment, LogsDialogFragment())
        }
    }

    // == UI
    private var binding: DialogToolsAppLogsBinding? = null

    private val itemAdapter = ItemAdapter<TextItem<DocumentFile>>()
    val fastadapter = FastAdapter.with(itemAdapter)


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogToolsAppLogsBinding.inflate(inflater, container, false)

        binding?.logsList?.adapter = fastadapter
        fastadapter.onClickListener = { _, _, i, _ -> onItemClick(i) }

        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        lifecycleScope.launch {
            try {
                val files = getLogs()
                onGetSuccess(files)
            } catch (e: Exception) {
                Timber.w(e)
            }
        }
    }

    private fun onGetSuccess(files: List<DocumentFile>) {
        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ENGLISH)
        for (file in files) {
            var fileName = file.name
            fileName = fileName?.lowercase(Locale.getDefault()) ?: ""
            val timeStr = formatEpochToDate(file.lastModified(), formatter)
            val label = "$fileName ($timeStr)"
            itemAdapter.add(TextItem(label, file, false))
        }
    }

    private suspend fun getLogs(): List<DocumentFile> {
        return withContext(Dispatchers.IO) {
            val rootFolder =
                getDocumentFromTreeUriString(
                    requireContext(),
                    Settings.getStorageUri(StorageLocation.PRIMARY_1)
                ) ?: return@withContext emptyList<DocumentFile>()

            var files = listFiles(
                requireContext(), rootFolder
            ) { displayName: String ->
                displayName.lowercase(
                    Locale.getDefault()
                ).endsWith("_log.txt")
            }
            // Sort by date desc
            files = files.sortedByDescending { f -> f.lastModified() }
            return@withContext files
        }
    }

    private fun onItemClick(item: TextItem<DocumentFile>): Boolean {
        val document = item.getObject() ?: return false
        val powerMenu = PowerMenu.Builder(requireContext())
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.logs_open),
                    false,
                    R.drawable.ic_action_open_in_new
                )
            )
            .addItem(
                PowerMenuItem(
                    resources.getString(R.string.logs_share),
                    false,
                    R.drawable.ic_action_share
                )
            )
            .setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
            .setMenuRadius(10f)
            .setLifecycleOwner(requireActivity())
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87))
            .setTextTypeface(Typeface.DEFAULT)
            .setMenuColor(ContextCompat.getColor(requireContext(), R.color.dark_gray))
            .setTextSize(dimensAsDp(requireContext(), R.dimen.text_subtitle_1))
            .setAutoDismiss(true)
            .build()
        powerMenu.onMenuItemClickListener =
            OnMenuItemClickListener { p, _ ->
                if (0 == p) {
                    openFile(requireContext(), document)
                } else {
                    shareFile(
                        requireContext(),
                        document.uri,
                        item.text
                    )
                }
            }
        powerMenu.setIconColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87))
        powerMenu.showAtCenter(binding?.root)
        return true
    }
}