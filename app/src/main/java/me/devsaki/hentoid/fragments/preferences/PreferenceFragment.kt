package me.devsaki.hentoid.fragments.preferences

import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.DrawerEditActivity
import me.devsaki.hentoid.activities.PinPreferenceActivity
import me.devsaki.hentoid.core.startLocalActivity
import me.devsaki.hentoid.core.withArguments
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.fragments.DeleteProgressDialogFragment
import me.devsaki.hentoid.services.ExternalImportService
import me.devsaki.hentoid.services.UpdateCheckService
import me.devsaki.hentoid.util.FileHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.viewmodels.PreferencesViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.ImportWorker
import me.devsaki.hentoid.workers.UpdateDownloadWorker


class PreferenceFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    lateinit var viewModel: PreferencesViewModel
    private var rootView: View? = null

    companion object {
        private const val KEY_ROOT = "root"

        fun newInstance(rootKey: String?): PreferenceFragment {
            val fragment = PreferenceFragment()
            if (rootKey != null) {
                val args = Bundle()
                args.putCharSequence(KEY_ROOT, rootKey)
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments
        if (arguments != null && arguments.containsKey(KEY_ROOT)) {
            val root = arguments.getCharSequence(KEY_ROOT)
            if (root != null) preferenceScreen = findPreference(root)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel =
            ViewModelProvider(requireActivity(), vmFactory)[PreferencesViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        preferenceScreen.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
        rootView = null // Avoid leaks
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        onHentoidFolderChanged()
        onExternalFolderChanged()
        populateMemoryUsage()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            Preferences.Key.COLOR_THEME -> onPrefColorThemeChanged()
            Preferences.Key.DL_THREADS_QUANTITY_LISTS,
            Preferences.Key.APP_PREVIEW,
            Preferences.Key.ANALYTICS_PREFERENCE -> onPrefRequiringRestartChanged()
            Preferences.Key.SETTINGS_FOLDER,
            Preferences.Key.SD_STORAGE_URI -> onHentoidFolderChanged()
            Preferences.Key.EXTERNAL_LIBRARY_URI -> onExternalFolderChanged()
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
        when (preference.key) {
            Preferences.Key.DRAWER_SOURCES -> {
                requireContext().startLocalActivity<DrawerEditActivity>()
                true
            }
            Preferences.Key.EXTERNAL_LIBRARY -> {
                if (ExternalImportService.isRunning()) {
                    ToastHelper.toast(R.string.pref_import_running)
                } else {
                    LibRefreshDialogFragment.invoke(parentFragmentManager, false, true, true)
                }
                true
            }
            Preferences.Key.EXTERNAL_LIBRARY_DETACH -> {
                MaterialAlertDialogBuilder(
                    requireContext(),
                    ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog)
                )
                    .setIcon(R.drawable.ic_warning)
                    .setCancelable(true)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.prefs_ask_detach_external_library)
                    .setPositiveButton(
                        R.string.yes
                    ) { dialog1: DialogInterface, _: Int ->
                        dialog1.dismiss()
                        Preferences.setExternalLibraryUri("")
                        viewModel.removeAllExternalContent()
                        ToastHelper.toast(R.string.prefs_external_library_detached)
                    }
                    .setNegativeButton(
                        R.string.no
                    ) { dialog12: DialogInterface, _: Int -> dialog12.dismiss() }
                    .create()
                    .show()
                true
            }
            Preferences.Key.REFRESH_LIBRARY -> {
                if (ImportWorker.isRunning(requireContext())) {
                    ToastHelper.toast(R.string.pref_import_running)
                } else {
                    LibRefreshDialogFragment.invoke(parentFragmentManager, true, false, false)
                }
                true
            }
            Preferences.Key.DELETE_ALL_EXCEPT_FAVS -> {
                onDeleteAllExceptFavourites()
                true
            }
            Preferences.Key.VIEWER_RENDERING -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                    ToastHelper.toast(R.string.pref_viewer_rendering_no_android5)
                true
            }
            Preferences.Key.SETTINGS_FOLDER -> {
                if (ImportWorker.isRunning(requireContext())) {
                    ToastHelper.toast(R.string.pref_import_running)
                } else {
                    LibRefreshDialogFragment.invoke(parentFragmentManager, false, true, false)
                }
                true
            }
            Preferences.Key.MEMORY_USAGE -> {
                MemoryUsageDialogFragment.invoke(parentFragmentManager)
                true
            }
            Preferences.Key.APP_LOCK -> {
                requireContext().startLocalActivity<PinPreferenceActivity>()
                true
            }
            Preferences.Key.CHECK_UPDATE_MANUAL -> {
                onCheckUpdatePrefClick()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        val preferenceFragment = PreferenceFragment().withArguments {
            putString(ARG_PREFERENCE_ROOT, preferenceScreen.key)
        }

        parentFragmentManager.commit(true) {
            replace(android.R.id.content, preferenceFragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }

    private fun onCheckUpdatePrefClick() {
        if (!UpdateDownloadWorker.isRunning(requireContext())) {
            val intent = UpdateCheckService.makeIntent(requireContext(), true)
            requireContext().startService(intent)
        }
    }

    private fun onPrefRequiringRestartChanged() {
        ToastHelper.toast(R.string.restart_needed)
    }

    private fun onHentoidFolderChanged() {
        val storageFolderPref: Preference? =
            findPreference(Preferences.Key.SETTINGS_FOLDER) as Preference?
        val uri = Uri.parse(Preferences.getStorageUri())
        storageFolderPref?.summary = FileHelper.getFullPathFromTreeUri(requireContext(), uri)
    }

    private fun onExternalFolderChanged() {
        val storageFolderPref: Preference? =
            findPreference(Preferences.Key.EXTERNAL_LIBRARY) as Preference?
        val uri = Uri.parse(Preferences.getExternalLibraryUri())
        storageFolderPref?.summary = FileHelper.getFullPathFromTreeUri(requireContext(), uri)
        // Enable/disable sub-prefs
        val deleteExternalLibrary: Preference? =
            findPreference(Preferences.Key.EXTERNAL_LIBRARY_DELETE) as Preference?
        deleteExternalLibrary?.isEnabled = (uri.toString().isNotEmpty())
        val detachExternalLibrary: Preference? =
            findPreference(Preferences.Key.EXTERNAL_LIBRARY_DETACH) as Preference?
        detachExternalLibrary?.isEnabled = (uri.toString().isNotEmpty())
    }

    private fun onPrefColorThemeChanged() {
        ThemeHelper.applyTheme(requireActivity() as AppCompatActivity)
    }

    private fun populateMemoryUsage() {
        val folder =
            FileHelper.getFolderFromTreeUriString(requireContext(), Preferences.getStorageUri())
                ?: return

        val memUsagePref: Preference? = findPreference(Preferences.Key.MEMORY_USAGE) as Preference?
        memUsagePref?.summary = resources.getString(
            R.string.pref_memory_usage_summary,
            FileHelper.MemoryUsageFigures(requireContext(), folder).freeUsageRatio100
        )
    }

    private fun onDeleteAllExceptFavourites() {
        val dao = ObjectBoxDAO(activity)
        var searchDisposable = Disposables.empty()

        searchDisposable = Single.fromCallable { dao.selectStoredContentIds(true, false, -1, false) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { list ->
                MaterialAlertDialogBuilder(
                    requireContext(),
                    ThemeHelper.getIdForCurrentTheme(requireContext(), R.style.Theme_Light_Dialog)
                )
                    .setIcon(R.drawable.ic_warning)
                    .setCancelable(false)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.pref_ask_delete_all_except_favs, list.size))
                    .setPositiveButton(
                        R.string.yes
                    ) { dialog1: DialogInterface, _: Int ->
                        dao.cleanup()
                        dialog1.dismiss()
                        searchDisposable.dispose()
                        DeleteProgressDialogFragment.invoke(
                            parentFragmentManager,
                            resources.getString(R.string.delete_title)
                        )
                        viewModel.deleteAllItemsExceptFavourites()
                    }
                    .setNegativeButton(
                        R.string.no
                    ) { dialog12: DialogInterface, _: Int ->
                        dao.cleanup()
                        dialog12.dismiss()
                    }
                    .create()
                    .show()
            }
    }
}