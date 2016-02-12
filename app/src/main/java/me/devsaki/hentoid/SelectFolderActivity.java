package me.devsaki.hentoid;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;

/**
 * Allows the user to select where their library will be saved.
 * TODO: Remove/rework this functionality.
 * TODO: User should only be saving where Android allows.
 * Ref: https://commonsware.com/blog/2014/04/08/storage-situation-external-storage.html
 */
public class SelectFolderActivity extends AppCompatActivity implements
        DirectoryChooserFragment.OnFragmentInteractionListener {

    private DirectoryChooserFragment mDialog;
    private SharedPreferences.Editor editor;

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_folder);

        mDialog = DirectoryChooserFragment.newInstance(Constants.DEFAULT_LOCAL_DIRECTORY, null);

        SharedPreferences prefs = HentoidApplication.getAppPreferences();
        editor = prefs.edit();
        String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        if (!settingDir.isEmpty()) {
            EditText editText = (EditText) findViewById(R.id.etFolder);
            editText.setText(settingDir);
        } else {
            selectDefault(null);
        }
    }

    @SuppressWarnings("UnusedParameters")
    public void explore(View view) {
        mDialog.show(getFragmentManager(), null);
    }

    @SuppressWarnings("UnusedParameters")
    public void selectDefault(View view) {
        EditText editText = (EditText) findViewById(R.id.etFolder);
        editText.setText(Helper.getDefaultDir("", this).getAbsolutePath());
    }

    @SuppressWarnings("UnusedParameters")
    public void save(View view) {
        EditText editText = (EditText) findViewById(R.id.etFolder);
        String hentoidFolder = editText.getText().toString();

        //Validation folder
        File file = new File(hentoidFolder);
        if (!file.exists() && !file.isDirectory()) {
            if (!file.mkdirs()) {
                Toast.makeText(this, R.string.error_creating_folder, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        File nomedia = new File(hentoidFolder, ".nomedia");
        boolean hasPermission;
        try {
            if (nomedia.exists()) {
                //noinspection ResultOfMethodCallIgnored
                nomedia.delete();
            }
            hasPermission = nomedia.createNewFile();
        } catch (IOException e) {
            hasPermission = false;
        }
        if (!hasPermission) {
            Toast.makeText(this, R.string.error_write_permission,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        editor.putString(Constants.SETTINGS_FOLDER, hentoidFolder);
        boolean directorySaved = editor.commit();
        if (!directorySaved) {
            Toast.makeText(this, R.string.error_creating_folder, Toast.LENGTH_SHORT).show();
            return;
        }
        List<File> downloadDirs = new ArrayList<>();
        for (Site s : Site.values()) {
            downloadDirs.add(Helper.getDownloadDir(s, this));
        }
        List<File> files = new ArrayList<>();
        for (File downloadDir : downloadDirs) {
            File[] contentFiles = downloadDir.listFiles();
            if (contentFiles != null)
                files.addAll(Arrays.asList(contentFiles));
        }
        if (files.size() > 0) {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.detect_contents)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(SelectFolderActivity.this,
                                    ImporterActivity.class);
                            startActivity(intent);
                            finish();
                        }

                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }

                    })
                    .show();
        } else {
            Intent intent = new Intent(this, DownloadsActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    public void onSelectDirectory(@NonNull String s) {
        EditText editText = (EditText) findViewById(R.id.etFolder);
        editText.setText(s);
        mDialog.dismiss();
    }

    @Override
    public void onCancelChooser() {
        mDialog.dismiss();
    }
}