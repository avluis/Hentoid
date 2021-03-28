package me.devsaki.hentoid.fragments.tools

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DuplicateDetectorActivity
import me.devsaki.hentoid.fragments.preferences.MetaExportDialogFragment
import me.devsaki.hentoid.fragments.preferences.MetaImportDialogFragment
import me.devsaki.hentoid.fragments.preferences.SettingsImportDialogFragment
import me.devsaki.hentoid.json.JsonSettings
import me.devsaki.hentoid.util.*
import me.devsaki.hentoid.viewmodels.PreferencesViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import org.apache.commons.io.IOUtils
import timber.log.Timber
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*


class ToolsFragment : PreferenceFragmentCompat() {

    private val DUPLICATE_DETECTOR_KEY = "tools_duplicate_detector"
    private val EXPORT_LIBRARY = "export_library"
    private val IMPORT_LIBRARY = "import_library"
    private val EXPORT_SETTINGS = "export_settings"
    private val IMPORT_SETTINGS = "import_settings"


    lateinit var viewModel: PreferencesViewModel
    lateinit var exportDisposable: Disposable
    private var rootView: View? = null

    companion object {
        fun newInstance(): ToolsFragment {
            return ToolsFragment()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[PreferencesViewModel::class.java]
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
        exportDisposable = io.reactivex.Single.fromCallable { getExportedSettings() }
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.schedulers.Schedulers.io())
                .map { c: JsonSettings? -> JsonHelper.serializeToJson<JsonSettings?>(c, JsonSettings::class.java) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { s: String -> onJsonSerialized(s) }, { t: Throwable? -> Timber.w(t) }
                )
    }

    private fun getExportedSettings(): JsonSettings {
        val jsonSettings = JsonSettings()

        jsonSettings.settings = Preferences.extractPortableInformation()

        return jsonSettings
    }

    private fun onJsonSerialized(json: String) {
        exportDisposable.dispose()

        // Use a random number to avoid erasing older exports by mistake
        var targetFileName = Random().nextInt(9999).toString() + ".json"
        targetFileName = "settings-$targetFileName"

        rootView?.let {
            try {
                FileHelper.openNewDownloadOutputStream(requireContext(), targetFileName, JsonHelper.JSON_MIME_TYPE).use { newDownload -> IOUtils.toInputStream(json, StandardCharsets.UTF_8).use { input -> FileHelper.copy(input, newDownload) } }
                Snackbar.make(it, R.string.copy_download_folder_success, BaseTransientBottomBar.LENGTH_LONG)
                        .setAction("OPEN FOLDER") { FileHelper.openFile(requireContext(), FileHelper.getDownloadsFolder()) }
                        .show()
            } catch (e: IOException) {
                Snackbar.make(it, R.string.copy_download_folder_fail, BaseTransientBottomBar.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                Snackbar.make(it, R.string.copy_download_folder_fail, BaseTransientBottomBar.LENGTH_LONG).show()
            }
        }
    }
}