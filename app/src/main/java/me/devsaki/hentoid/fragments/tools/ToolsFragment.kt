package me.devsaki.hentoid.fragments.tools

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.activities.RenamingRulesActivity
import me.devsaki.hentoid.core.clearAppCache
import me.devsaki.hentoid.core.clearWebviewCache
import me.devsaki.hentoid.core.startLocalActivity
import me.devsaki.hentoid.core.withArguments
import me.devsaki.hentoid.fragments.ProgressDialogFragment
import me.devsaki.hentoid.json.JsonSettings
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.workers.DeleteWorker
import me.devsaki.hentoid.workers.data.DeleteData
import okhttp3.internal.format
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets


@Suppress("PrivatePropertyName")
class ToolsFragment : PreferenceFragmentCompat(), MassDeleteFragment.Companion.Parent {

    private val DUPLICATE_DETECTOR_KEY = "tools_duplicate_detector"
    private val EXPORT_LIBRARY = "export_library"
    private val IMPORT_LIBRARY = "import_library"
    private val EXPORT_SETTINGS = "export_settings"
    private val IMPORT_SETTINGS = "import_settings"
    private val ACCESS_RENAMING_RULES = "tools_renaming_rules"
    private val RAM = "tools_ram_usage"
    private val ACCESS_LATEST_LOGS = "tools_latest_logs"
    private val CLEAR_BROWSER_CACHE = "cache_browser"
    private val CLEAR_APP_CACHE = "cache_app"

    private var rootView: View? = null

    companion object {
        fun newInstance(): ToolsFragment {
            return ToolsFragment()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
    }

    override fun onDestroy() {
        rootView = null // Avoid leaks
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.tools, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
        when (preference.key) {
            DUPLICATE_DETECTOR_KEY -> {
                requireContext().startLocalActivity<DuplicateDetectorActivity>()
                true
            }

            Preferences.Key.DELETE_ALL_EXCEPT_FAVS -> {
                MassDeleteFragment.invoke(this.childFragmentManager)
                true
            }

            EXPORT_LIBRARY -> {
                MetaExportDialogFragment.invoke(parentFragmentManager)
                true
            }

            IMPORT_LIBRARY -> {
                MetaImportDialogFragment.invoke(parentFragmentManager)
                true
            }

            EXPORT_SETTINGS -> {
                onExportSettings()
                true
            }

            IMPORT_SETTINGS -> {
                SettingsImportDialogFragment.invoke(parentFragmentManager)
                true
            }

            CLEAR_BROWSER_CACHE -> {
                context?.clearWebviewCache {
                    ToastHelper.toast(
                        if (it) R.string.tools_cache_browser_success else
                            if (WebkitPackageHelper.getWebViewUpdating()) R.string.tools_cache_browser_updating_webview
                            else R.string.tools_cache_browser_missing_webview
                    )
                }
                true
            }

            CLEAR_APP_CACHE -> {
                context?.clearAppCache()
                ToastHelper.toast(R.string.tools_cache_app_success)
                true
            }

            ACCESS_RENAMING_RULES -> {
                requireContext().startLocalActivity<RenamingRulesActivity>()
                true
            }

            RAM -> {
                val usedAppHeap =
                    FileHelper.formatHumanReadableSize(Helper.getAppHeapBytes().first, resources)
                val usedAppTotal =
                    FileHelper.formatHumanReadableSize(Helper.getAppTotalRamBytes(), resources)
                val systemHeap = Helper.getSystemHeapBytes(activity)
                val systemHeapUsed = FileHelper.formatHumanReadableSize(
                    systemHeap.first,
                    resources
                )
                val systemHeapFree = FileHelper.formatHumanReadableSize(
                    systemHeap.second,
                    resources
                )
                val msg = format(
                    "Used app RAM (heap) : %s\nUsed app RAM (total) : %s\nSystem heap (used) : %s\nSystem heap (free) : %s",
                    usedAppHeap,
                    usedAppTotal,
                    systemHeapUsed,
                    systemHeapFree
                )
                val materialDialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
                    .setMessage(msg)
                    .setCancelable(true)
                    .setNeutralButton(android.R.string.ok) { i: DialogInterface, _: Int -> i.dismiss() }
                    .create()

                materialDialog.setIcon(R.drawable.ic_memory)
                materialDialog.show()
                true
            }

            ACCESS_LATEST_LOGS -> {
                LogsDialogFragment.invoke(parentFragmentManager)
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        val preferenceFragment = ToolsFragment().withArguments {
            putString(ARG_PREFERENCE_ROOT, preferenceScreen.key)
        }

        parentFragmentManager.commit(true) {
            replace(android.R.id.content, preferenceFragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }

    private fun onExportSettings() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val settings = getExportedSettings()
                    return@withContext JsonHelper.serializeToJson(
                        settings,
                        JsonSettings::class.java
                    )
                } catch (e: Exception) {
                    Timber.w(e)
                }
                return@withContext ""
            }
            coroutineScope {
                if (result.isNotEmpty()) onJsonSerialized(result)
            }
        }
    }

    private fun getExportedSettings(): JsonSettings {
        val jsonSettings = JsonSettings()

        jsonSettings.settings = Preferences.extractPortableInformation()

        return jsonSettings
    }

    private fun onJsonSerialized(json: String) {
        // Use a random number to avoid erasing older exports by mistake
        var targetFileName = Helper.getRandomInt(9999).toString() + ".json"
        targetFileName = "settings-$targetFileName"

        rootView?.let {
            try {
                FileHelper.openNewDownloadOutputStream(
                    requireContext(),
                    targetFileName,
                    JsonHelper.JSON_MIME_TYPE
                ).use { newDownload ->
                    ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
                        .use { input ->
                            Helper.copy(
                                input,
                                newDownload
                            )
                        }
                }
                Snackbar.make(
                    it,
                    R.string.copy_download_folder_success,
                    BaseTransientBottomBar.LENGTH_LONG
                )
                    .setAction(R.string.open_folder) {
                        FileHelper.openFile(
                            requireContext(),
                            FileHelper.getDownloadsFolder()
                        )
                    }
                    .show()
            } catch (e: IOException) {
                Snackbar.make(
                    it,
                    R.string.copy_download_folder_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            } catch (e: IllegalArgumentException) {
                Snackbar.make(
                    it,
                    R.string.copy_download_folder_fail,
                    BaseTransientBottomBar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onMassDelete(keepBookPrefs: Boolean, keepGroupPrefs: Boolean) {
        ProgressDialogFragment.invoke(
            parentFragmentManager,
            resources.getString(R.string.delete_title),
            R.plurals.book
        )

        val builder = DeleteData.Builder()
        builder.setDeleteAllContentExceptFavsBooks(keepBookPrefs)
        builder.setDeleteAllContentExceptFavsGroups(keepGroupPrefs)

        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueueUniqueWork(
            R.id.delete_service_delete.toString(),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DeleteWorker>()
                .setInputData(builder.data)
                .build()
        )
    }
}