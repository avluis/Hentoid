package me.devsaki.hentoid.fragments

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import me.devsaki.hentoid.HentoidApp
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.PinPreferenceActivity
import me.devsaki.hentoid.fragments.import_.LibRefreshDialogFragment
import me.devsaki.hentoid.services.ImportService
import me.devsaki.hentoid.services.UpdateCheckService
import me.devsaki.hentoid.services.UpdateDownloadService
import me.devsaki.hentoid.util.*

class MyPreferenceFragment : PreferenceFragmentCompat() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        findPreference<Preference>(Preferences.Key.DARK_MODE)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    onPrefDarkModeChanged(newValue)
                }
        findPreference<Preference>(Preferences.Key.PREF_DL_THREADS_QUANTITY_LISTS)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    onPrefRequiringRestartChanged()
                }
        findPreference<Preference>(Preferences.Key.PREF_APP_PREVIEW)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    onPrefRequiringRestartChanged()
                }
        findPreference<Preference>(Preferences.Key.PREF_ANALYTICS_PREFERENCE)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    onPrefRequiringRestartChanged()
                }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean =
            when (preference.key) {
                Preferences.Key.PREF_ADD_NO_MEDIA_FILE -> {
                    FileHelper.createNoMedia()
                    true
                }
                Preferences.Key.PREF_CHECK_UPDATE_MANUAL -> {
                    onCheckUpdatePrefClick()
                    true
                }
                Preferences.Key.PREF_REFRESH_LIBRARY -> {
                    if (ImportService.isRunning()) {
                        ToastUtil.toast("Import is already running")
                    } else {
                        LibRefreshDialogFragment.invoke(requireFragmentManager())
                    }
                    true
                }
                Preferences.Key.PREF_APP_LOCK -> {
                    requireContext().startLocalActivity<PinPreferenceActivity>()
                    true
                }
                else -> super.onPreferenceTreeClick(preference)
            }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        val preferenceFragment = MyPreferenceFragment().withArguments {
            putString(ARG_PREFERENCE_ROOT, preferenceScreen.key)
        }

        requireFragmentManager().commit {
            replace(android.R.id.content, preferenceFragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }

    private fun onCheckUpdatePrefClick() {
        if (!UpdateDownloadService.isRunning()) {
            val intent = UpdateCheckService.makeIntent(requireContext(), true)
            requireContext().startService(intent)
        }
    }

    private fun onPrefRequiringRestartChanged(): Boolean {
        ToastUtil.toast(R.string.restart_needed)
        return true
    }

    private fun onPrefDarkModeChanged(value: Any): Boolean {
        AppCompatDelegate.setDefaultNightMode(HentoidApp.darkModeFromPrefs(value.toString().toInt()))
        return true
    }
}