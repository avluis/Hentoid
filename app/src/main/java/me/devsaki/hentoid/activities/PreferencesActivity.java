package me.devsaki.hentoid.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.updater.UpdateCheck;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.ConstantsPreferences;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by DevSaki on 20/05/2015.
 * Present and set app preferences.
 */
public class PreferencesActivity extends AppCompatActivity {
    private static final String TAG = LogHelper.makeLogTag(PreferencesActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new MyPreferenceFragment()).commit();

        AndroidHelper.setNavBarColor(this, "#2b0202");

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        // Allow super to try and create a view first
        final View result = super.onCreateView(name, context, attrs);
        if (result != null) {
            return result;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // If we're running pre-L, we need to 'inject' our tint aware Views in place of the
            // standard framework versions
            switch (name) {
                case "EditText":
                    return new AppCompatEditText(this, attrs);
                case "Spinner":
                    return new AppCompatSpinner(this, attrs);
                case "CheckBox":
                    return new AppCompatCheckBox(this, attrs);
                case "RadioButton":
                    return new AppCompatRadioButton(this, attrs);
                case "CheckedTextView":
                    return new AppCompatCheckedTextView(this, attrs);
            }
        }

        return null;
    }

    public static class MyPreferenceFragment extends PreferenceFragment {

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);

            Preference addNoMediaFile = getPreferenceScreen()
                    .findPreference("pref_add_no_media_file");
            addNoMediaFile.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences prefs = HentoidApplication.getAppPreferences();
                    String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
                    File nomedia = new File(settingDir, ".nomedia");
                    if (!nomedia.exists()) {
                        try {
                            // noinspection ResultOfMethodCallIgnored
                            nomedia.createNewFile();
                        } catch (IOException e) {
                            Toast.makeText(getActivity(), R.string.error_creating_nomedia_file,
                                    Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    }
                    Toast.makeText(getActivity(), R.string.nomedia_file_created,
                            Toast.LENGTH_SHORT).show();

                    return true;
                }
            });

            Preference appLock = getPreferenceScreen().findPreference("pref_app_lock");
            appLock.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.app_lock_pin_prefs);
                    final EditText input = new EditText(getActivity());
                    input.setGravity(Gravity.CENTER);
                    input.setInputType(InputType.TYPE_CLASS_NUMBER);
                    builder.setView(input);
                    builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                                saveKey(dialog, input);

                                return true;
                            }

                            return false;
                        }
                    });
                    builder.setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    saveKey(dialog, input);
                                }
                            });
                    builder.setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.cancel();
                                }
                            });
                    builder.show();

                    return true;
                }
            });

            Preference mUpdateCheck = getPreferenceScreen()
                    .findPreference("pref_check_updates_manual");
            mUpdateCheck.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Toast.makeText(getActivity().getApplicationContext(), "Checking for updates...",
                            Toast.LENGTH_SHORT).show();
                    UpdateCheck.getInstance().checkForUpdate(getActivity().getApplicationContext(),
                            false, true,
                            new UpdateCheck.UpdateCheckCallback() {
                                @Override
                                public void noUpdateAvailable() {
                                    LogHelper.i(TAG, "Manual update check: No update available.");
                                }

                                @Override
                                public void onUpdateAvailable() {
                                    LogHelper.d(TAG, "Manual update check: Update available!");
                                }
                            });

                    return true;
                }
            });
        }

        private void saveKey(DialogInterface dialog, EditText input) {
            String lock = input.getText().toString();
            SharedPreferences.Editor editor = HentoidApplication
                    .getAppPreferences().edit();
            editor.putString(ConstantsPreferences.PREF_APP_LOCK, lock);
            editor.apply();
            if (lock.isEmpty()) {
                Toast.makeText(getActivity(), R.string.app_lock_disable,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), R.string.app_lock_enable,
                        Toast.LENGTH_SHORT).show();
            }
            dialog.cancel();
        }
    }
}