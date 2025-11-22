package me.devsaki.hentoid.fragments.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.allViews
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
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
import me.devsaki.hentoid.activities.bundles.SettingsSourceSpecificsBundle
import me.devsaki.hentoid.activities.settings.SettingsPinActivity
import me.devsaki.hentoid.activities.settings.SettingsSourceSelectActivity
import me.devsaki.hentoid.activities.settings.SettingsSourceSpecificsActivity
import me.devsaki.hentoid.activities.settings.SettingsStorageActivity
import me.devsaki.hentoid.core.startLocalActivity
import me.devsaki.hentoid.core.withArguments
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.Theme
import me.devsaki.hentoid.retrofit.DeviantArtServer
import me.devsaki.hentoid.retrofit.GithubServer
import me.devsaki.hentoid.retrofit.JikanServer
import me.devsaki.hentoid.retrofit.sources.EHentaiServer
import me.devsaki.hentoid.retrofit.sources.KemonoServer
import me.devsaki.hentoid.retrofit.sources.LusciousServer
import me.devsaki.hentoid.retrofit.sources.PixivServer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.applyTheme
import me.devsaki.hentoid.util.download.DownloadSpeedLimiter
import me.devsaki.hentoid.util.download.RequestQueueManager
import me.devsaki.hentoid.util.file.getFullPathFromUri
import me.devsaki.hentoid.viewmodels.SettingsViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.workers.UpdateCheckWorker
import me.devsaki.hentoid.workers.UpdateDownloadWorker


// Value of key elements on the preferences tree
private const val CHECK_UPDATE_MANUAL = "pref_check_updates_manual"
private const val DRAWER_SOURCES = "pref_drawer_sources"
private const val SOURCE_SPECIFICS = "pref_source_specifics"
private const val EXTERNAL_LIBRARY = "pref_external_library"
private const val EXTERNAL_LIBRARY_DETACH = "pref_detach_external_library"
private const val STORAGE_MANAGEMENT = "storage_mgt"

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    lateinit var viewModel: SettingsViewModel
    lateinit var root: View
    var site = Site.NONE

    companion object {
        private const val KEY_ROOT = "root"
        private const val KEY_SITE = "site"

        fun newInstance(rootKey: String?, site: Site): SettingsFragment {
            val fragment = SettingsFragment()
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
                site = Site.searchByCode(arguments.getInt(KEY_SITE))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.fitsSystemWindows = true

        // Get a view to display snackbars against
        root = view

        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel =
            ViewModelProvider(requireActivity(), vmFactory)[SettingsViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()

        // Update summaries
        for (i in 0..<preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            if (preference is PreferenceGroup) {
                val preferenceGroup: PreferenceGroup = preference
                for (j in 0..<preferenceGroup.preferenceCount) {
                    val singlePref = preferenceGroup.getPreference(j)
                    updatePreferenceSummary(singlePref, singlePref.key)
                }
            } else {
                updatePreferenceSummary(preference, preference.key)
            }
        }

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

        (findPreference(key) as Preference?)?.let { updatePreferenceSummary(it, key) }

        when (key) {
            Settings.Key.COLOR_THEME -> onPrefColorThemeChanged()
            Settings.Key.DL_THREADS_QUANTITY_LISTS,
            Settings.Key.APP_PREVIEW,
            Settings.Key.FORCE_ENGLISH,
            Settings.Key.TEXT_SELECT_MENU,
            Settings.Key.ANALYTICS_PREFERENCE -> showSnackbar(R.string.restart_needed)

            Settings.Key.EXTERNAL_LIBRARY_URI -> onExternalFolderChanged()
            Settings.Key.BROWSER_DNS_OVER_HTTPS -> onDoHChanged()
            Settings.Key.BROWSER_PROXY -> onProxyChanged()
            Settings.Key.WEB_AUGMENTED_BROWSER -> onAugmentedBrowserChanged()
        }
    }

    private fun updatePreferenceSummary(preference: Preference, key: String?) {
        if (null == key) return
        if (Settings.Key.APP_LOCK == key) return // Don't display that ^^"
        if (preference is CheckBoxPreference) return
        if (preference is ListPreference) return
        preference.setSummary(preference.sharedPreferences?.getString(key, "") ?: "")
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
        when (preference.key) {
            DRAWER_SOURCES -> {
                requireContext().startLocalActivity<SettingsSourceSelectActivity>()
                true
            }

            SOURCE_SPECIFICS -> {
                val intent = Intent(context, SettingsSourceSpecificsActivity::class.java)
                val outBundle = SettingsSourceSpecificsBundle()
                outBundle.site = site.code
                intent.putExtras(outBundle.bundle)
                requireContext().startActivity(intent)
                true
            }

            STORAGE_MANAGEMENT -> {
                requireContext().startLocalActivity<SettingsStorageActivity>()
                true
            }

            Settings.Key.APP_LOCK -> {
                requireContext().startLocalActivity<SettingsPinActivity>()
                true
            }

            CHECK_UPDATE_MANUAL -> {
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

            "download_schedule" -> {
                TimeRangeDialogFragment.invoke(this)
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }

    fun navigateToScreen(manager: FragmentManager, screenKey: String): SettingsFragment {
        val preferenceFragment = SettingsFragment().withArguments {
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

    private fun onExternalFolderChanged() {
        val storageFolderPref: Preference? =
            findPreference(EXTERNAL_LIBRARY) as Preference?
        val uri = Settings.externalLibraryUri.toUri()
        storageFolderPref?.summary = getFullPathFromUri(requireContext(), uri)
        // Enable/disable sub-prefs
        val deleteExternalLibrary: Preference? =
            findPreference(Settings.Key.EXTERNAL_LIBRARY_DELETE) as Preference?
        deleteExternalLibrary?.isEnabled = (uri.toString().isNotEmpty())
        val detachExternalLibrary: Preference? =
            findPreference(EXTERNAL_LIBRARY_DETACH) as Preference?
        detachExternalLibrary?.isEnabled = (uri.toString().isNotEmpty())
    }

    private fun onPrefColorThemeChanged() {
        // Material You doesn't exist before API31
        if (Build.VERSION.SDK_INT < 31 && Settings.colorTheme == Theme.YOU.id) {
            showSnackbar(R.string.material_you_warning)
            Settings.colorTheme = Theme.LIGHT.id
        } else (requireActivity() as AppCompatActivity).applyTheme()
    }

    private fun onDoHChanged() {
        if (Settings.dnsOverHttps > -1) showSnackbar(R.string.doh_warning)
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
                KemonoServer.init()
                JikanServer.init()
            }
        }
    }

    private fun onProxyChanged() {
        if (Settings.proxy.isNotEmpty()) showSnackbar(R.string.proxy_warning)
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
                KemonoServer.init()
                JikanServer.init()
            }
        }
    }

    private fun showSnackbar(strRes: Int) {
        val viewList = root.allViews.toList()
        val anchor = if (viewList.size > 1) viewList[1] else null

        anchor?.let {
            val snack = Snackbar.make(
                it,
                strRes,
                BaseTransientBottomBar.LENGTH_INDEFINITE
            )
            snack.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines =
                5
            snack.setAction(R.string.ok) { snack.dismiss() }
            snack.show()
        }
    }

    private fun onAugmentedBrowserChanged() {
        Settings.isAppAdBlockerOn = Settings.isAppBrowserAugmented
    }
}