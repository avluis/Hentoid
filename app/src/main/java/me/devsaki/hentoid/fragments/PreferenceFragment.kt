package me.devsaki.hentoid.fragments

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.PinPreferenceActivity
import me.devsaki.hentoid.enums.Theme
import me.devsaki.hentoid.fragments.import_.LibRefreshDialogFragment
import me.devsaki.hentoid.services.ImportService
import me.devsaki.hentoid.services.UpdateCheckService
import me.devsaki.hentoid.services.UpdateDownloadService
import me.devsaki.hentoid.util.*


class PreferenceFragment : PreferenceFragmentCompat() {


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (null == rootKey) { // Display general menu and root the viewer menu under the "viewer" group
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val viewerGroup = preferenceScreen.findPreference("viewer") as PreferenceGroup?
            addPreferencesFromResource(R.xml.preferences_viewer, viewerGroup)
        } else if (rootKey.startsWith("viewer")) { // Display viewer group submenu
            if (rootKey.equals("viewer"))
                addPreferencesFromResource(R.xml.preferences_viewer) // Root of the viewer submenu
            else
                setPreferencesFromResource(R.xml.preferences_viewer, rootKey) // One of the screens of the viewer submenu
        } else {
            setPreferencesFromResource(R.xml.preferences, rootKey) // Display one of the submenus of the general pref screen
        }

        findPreference<Preference>(Preferences.Key.PREF_COLOR_THEME)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    onPrefColorThemeChanged(newValue)
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
        val preferenceFragment = PreferenceFragment().withArguments {
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

    private fun onPrefColorThemeChanged(value: Any): Boolean {
        ThemeHelper.applyTheme(requireActivity() as AppCompatActivity, Theme.searchById(Integer.parseInt(value.toString())))
        return true
    }

    private fun addPreferencesFromResource(id: Int, newParent: PreferenceGroup?) {
        val screen = preferenceScreen
        val last = screen.preferenceCount
        addPreferencesFromResource(id)
        while (screen.preferenceCount > last) {
            val p = screen.getPreference(last)
            screen.removePreference(p) // decreases the preference count
            newParent?.addPreference(p)
        }
    }
}