package me.devsaki.hentoid;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.ConstantsPreferences;

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

            Preference appLock = getPreferenceScreen().findPreference("pref_app_lock");
            appLock.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.app_lock_pin);
                    final EditText input = new EditText(getActivity());
                    input.setGravity(Gravity.CENTER);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String lock = input.getText().toString();
                            SharedPreferences prefs = PreferenceManager
                                    .getDefaultSharedPreferences(getActivity());
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(ConstantsPreferences.PREF_APP_LOCK, lock);
                            editor.apply();
                            if (lock.isEmpty())
                                Toast.makeText(getActivity(), R.string.app_lock_disable, Toast.LENGTH_SHORT).show();
                            else
                                Toast.makeText(getActivity(), R.string.app_lock_enable, Toast.LENGTH_SHORT).show();
                            dialog.cancel();
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();
                    return true;
                }
            });
        }
    }
}
