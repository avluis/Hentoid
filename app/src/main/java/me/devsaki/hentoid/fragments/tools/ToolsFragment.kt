package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
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
import me.devsaki.hentoid.json.JsonSettings
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets


@Suppress("PrivatePropertyName")
class ToolsFragment : PreferenceFragmentCompat() {

    private val DUPLICATE_DETECTOR_KEY = "tools_duplicate_detector"
    private val EXPORT_LIBRARY = "export_library"
    private val IMPORT_LIBRARY = "import_library"
    private val EXPORT_SETTINGS = "export_settings"
    private val IMPORT_SETTINGS = "import_settings"
    private val ACCESS_RENAMING_RULES = "tools_renaming_rules"
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
}