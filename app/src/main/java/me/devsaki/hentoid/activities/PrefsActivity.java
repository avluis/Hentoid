package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.services.ImportService;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.services.UpdateDownloadService;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;

/**
 * Created by DevSaki on 20/05/2015.
 * Set up and present preferences.
 * <p>
 * Maintained by wightwulf1944 22/02/2018
 * updated class for new AppCompatActivity and cleanup
 */
public class PrefsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            findPreference(Preferences.Key.PREF_HIDE_RECENT)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_ANALYTICS_TRACKING)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_USE_SFW)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_DL_THREADS_QUANTITY_LISTS)
                    .setOnPreferenceChangeListener((preference, newValue) -> onPrefRequiringRestartChanged());

            findPreference(Preferences.Key.PREF_APP_LOCK)
                    .setOnPreferenceChangeListener((preference, newValue) -> onAppLockPinChanged(newValue));
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String key = preference.getKey();
            if (key == null) return super.onPreferenceTreeClick(preference);
            switch (key) {
                case Preferences.Key.PREF_ADD_NO_MEDIA_FILE:
                    return FileHelper.createNoMedia();
                case Preferences.Key.PREF_CHECK_UPDATE_MANUAL:
                    return onCheckUpdatePrefClick();
                case Preferences.Key.PREF_REFRESH_LIBRARY:
                    return onRefreshLibraryClick();
                default:
                    return super.onPreferenceTreeClick(preference);
            }
        }

        private boolean onCheckUpdatePrefClick() {
            if (!UpdateDownloadService.isRunning()) {
                Intent intent = UpdateCheckService.makeIntent(requireContext(), true);
                requireContext().startService(intent);
            }
            return true;
        }

        private void launchRefreshImport(boolean cleanup) {
            Intent refresh = new Intent(this.getContext(), ImportActivity.class);
            refresh.setAction("android.intent.action.APPLICATION_PREFERENCES"); // Is only a constant since API 24 -> using the string
            refresh.putExtra("refresh", true);
            refresh.putExtra("cleanup", cleanup);
            startActivityForResult(refresh, ConstsImport.RQST_IMPORT_RESULTS);
        }

        private boolean onRefreshLibraryClick() {
            if (!ImportService.isRunning()) {
                Context ctx = this.getContext();
                if (null == ctx) return false;

                new AlertDialog.Builder(ctx)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.cleanup_folders)
                        .setPositiveButton("Yes", // android.R.string.yes displays as "OK" ?! O.o
                                (dialog1, which) -> {
                                    dialog1.dismiss();
                                    launchRefreshImport(true);
                                })
                        .setNegativeButton("No", // // android.R.string.yes displays as "Cancel" ?! O.o
                                (dialog12, which) -> {
                                    dialog12.dismiss();
                                    launchRefreshImport(false);
                                })
                        .create()
                        .show();
            } else {
                Helper.toast("Import is already running");
            }
            return true;
        }

        private boolean onPrefRequiringRestartChanged() {
            Helper.toast(R.string.restart_needed);
            return true;
        }

        private boolean onAppLockPinChanged(Object newValue) {
            String pin = (String) newValue;
            if (pin.isEmpty()) {
                Helper.toast(getActivity(), R.string.app_lock_disabled);
            } else {
                Helper.toast(getActivity(), R.string.app_lock_enable);
            }
            return true;
        }
    }
}
