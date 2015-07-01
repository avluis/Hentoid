package me.devsaki.hentoid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import me.devsaki.hentoid.asynctasks.UpdateCheckerTask;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;

/**
 * Created by DevSaki on 20/05/2015.
 */
public class PreferencesActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            Preference addNoMediaFile = getPreferenceScreen().findPreference("pref_add_no_media_file");
            addNoMediaFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(getActivity());
                    String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
                    File nomedia = new File(settingDir, ".nomedia");
                    if (!nomedia.exists())
                        try {
                            nomedia.createNewFile();
                        } catch (IOException e) {
                            Toast.makeText(getActivity(), R.string.error_creating_nomedia_file, Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    Toast.makeText(getActivity(), R.string.nomedia_file_created, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            Preference checkUpdates = getPreferenceScreen().findPreference("pref_check_updates_now");
            checkUpdates.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AndroidHelper.executeAsyncTask(new UpdateCheckerTask(getActivity()));
                    return true;
                }
            });
        }
    }
}
