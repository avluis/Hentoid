package me.devsaki.hentoid.fragments.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.PrefsSourceSpecificsBundle
import me.devsaki.hentoid.activities.prefs.PreferencesPinActivity
import me.devsaki.hentoid.activities.prefs.PreferencesSourceSelectActivity
import me.devsaki.hentoid.activities.prefs.PreferencesSourceSpecificsActivity
import me.devsaki.hentoid.activities.prefs.PreferencesStorageActivity
import me.devsaki.hentoid.core.startLocalActivity
import me.devsaki.hentoid.core.withArguments
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.retrofit.DeviantArtServer
import me.devsaki.hentoid.retrofit.GithubServer
import me.devsaki.hentoid.retrofit.sources.EHentaiServer
import me.devsaki.hentoid.retrofit.sources.LusciousServer
import me.devsaki.hentoid.retrofit.sources.PixivServer
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter
import me.devsaki.hentoid.util.download.RequestQueueManager
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.viewmodels.PreferencesViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.UpdateCheckWorker
import me.devsaki.hentoid.workers.UpdateDownloadWorker


class PreferencesFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    lateinit var viewModel: PreferencesViewModel
    var site = Site.NONE

    companion object {
        private const val KEY_ROOT = "root"
        private const val KEY_SITE = "site"

        fun newInstance(rootKey: String?, site: Site): PreferencesFragment {
            val fragment = PreferencesFragment()
            if (rootKey != null) {
                val args = Bundle()
                args.putCharSequence(KEY_ROOT, rootKey)
                args.putInt(KEY_SITE, site.code)
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments
        if (arguments != null) {
            if (arguments.containsKey(KEY_ROOT)) {
                val root = arguments.getCharSequence(KEY_ROOT)
                if (root != null) preferenceScreen = findPreference(root)
            }
            if (arguments.containsKey(KEY_SITE))
                site = Site.searchByCode(arguments.getInt(KEY_SITE).toLong())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.fitsSystemWindows = true
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel =
            ViewModelProvider(requireActivity(), vmFactory)[PreferencesViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        onExternalFolderChanged()

        // Search bar
        (findPreference("searchPreference") as SearchPreference?)?.apply {
            val config = searchConfiguration
            config.setActivity(activity as AppCompatActivity)
            config.index(R.xml.preferences)
            config.setFuzzySearchEnabled(false)
        }

        // Numbers-only on delay input
        val editTextPreference =
            preferenceManager.findPreference<EditTextPreference>(Settings.Key.DL_HTTP_429_DEFAULT_DELAY)
        editTextPreference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (null == key) return
        when (key) {
            Settings.Key.COLOR_THEME -> onPrefColorThemeChanged()
            Settings.Key.DL_THREADS_QUANTITY_LISTS,
            Settings.Key.APP_PREVIEW,
            Settings.Key.FORCE_ENGLISH,
            Settings.Key.TEXT_SELECT_MENU,
            Settings.Key.ANALYTICS_PREFERENCE -> onPrefRequiringRestartChanged()

            Settings.Key.EXTERNAL_LIBRARY_URI -> onExternalFolderChanged()
            Settings.Key.BROWSER_DNS_OVER_HTTPS -> onDoHChanged()
            Settings.Key.WEB_AUGMENTED_BROWSER -> onAugmentedBrowserChanged()
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
        when (preference.key) {
            Preferences.Key.DRAWER_SOURCES -> {
                requireContext().startLocalActivity<PreferencesSourceSelectActivity>()
                true
            }

            Preferences.Key.SOURCE_SPECIFICS -> {
                val intent = Intent(context, PreferencesSourceSpecificsActivity::class.java)
                val outBundle = PrefsSourceSpecificsBundle()
                outBundle.site = site.code
                intent.putExtras(outBundle.bundle)
                requireContext().startActivity(intent)
                true
            }

            Preferences.Key.STORAGE_MANAGEMENT -> {
                requireContext().startLocalActivity<PreferencesStorageActivity>()
                true
            }

            Settings.Key.APP_LOCK -> {
                requireContext().startLocalActivity<PreferencesPinActivity>()
                true
            }

            Preferences.Key.CHECK_UPDATE_MANUAL -> {
                onCheckUpdatePrefClick()
                true
            }

            Settings.Key.DL_SPEED_CAP -> {
                DownloadSpeedLimiter.setSpeedLimitKbps(Settings.dlSpeedCap)
                true
            }

            Settings.Key.BROWSER_CLEAR_COOKIES -> {
                CookiesDialogFragment.invoke(this)
                true
            }

            "ext_import_pattern" -> {
                ImportNamePatternDialogFragment.invoke(this)
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }

    fun navigateToScreen(manager: FragmentManager, screenKey: String): PreferencesFragment {
        val preferenceFragment = PreferencesFragment().withArguments {
            putString(ARG_PREFERENCE_ROOT, screenKey)
        }

        manager.commit(true) {
            replace(android.R.id.content, preferenceFragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
        return preferenceFragment
    }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        navigateToScreen(parentFragmentManager, preferenceScreen.key)
    }

    private fun onCheckUpdatePrefClick() {
        if (!UpdateDownloadWorker.isRunning(requireContext())) {
            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.update_check_service.toString(),
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>().build()
            )
            // Toasts are handled at Activity level
        }
    }

    private fun onPrefRequiringRestartChanged() {
        toast(R.string.restart_needed)
    }

    private fun onExternalFolderChanged() {
        val storageFolderPref: Preference? =
            findPreference(Preferences.Key.EXTERNAL_LIBRARY) as Preference?
        val uri = Settings.externalLibraryUri.toUri()
        storageFolderPref?.summary = getFullPathFromUri(requireContext(), uri)
        // Enable/disable sub-prefs
        val deleteExternalLibrary: Preference? =
            findPreference(Settings.Key.EXTERNAL_LIBRARY_DELETE) as Preference?
        deleteExternalLibrary?.isEnabled = (uri.toString().isNotEmpty())
        val detachExternalLibrary: Preference? =
            findPreference(Preferences.Key.EXTERNAL_LIBRARY_DETACH) as Preference?
        detachExternalLibrary?.isEnabled = (uri.toString().isNotEmpty())
    }

    private fun onPrefColorThemeChanged() {
        (requireActivity() as AppCompatActivity).applyTheme()
    }

    private fun onDoHChanged() {
        if (Settings.dnsOverHttps > -1 && listView != null) {
            val snack = Snackbar.make(
                listView,
                R.string.doh_warning,
                BaseTransientBottomBar.LENGTH_INDEFINITE
            )
            snack.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines =
                5
            snack.setAction(R.string.ok) { snack.dismiss() }
            snack.show()
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Reset connection pool used by the downloader (includes an OkHttp instance reset)
                RequestQueueManager.getInstance()?.resetRequestQueue(true)
                // Reset all retrofit clients
                GithubServer.init()
                EHentaiServer.init()
                LusciousServer.init()
                PixivServer.init()
                DeviantArtServer.init()
            }
        }
    }

    private fun onAugmentedBrowserChanged() {
        Settings.isAppAdBlockerOn = Settings.isAppBrowserAugmented
    }
}