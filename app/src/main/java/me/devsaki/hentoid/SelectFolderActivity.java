package me.devsaki.hentoid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;

import java.io.File;
import java.io.IOException;


public class SelectFolderActivity extends Activity implements
        DirectoryChooserFragment.OnFragmentInteractionListener {

    private DirectoryChooserFragment mDialog;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_folder);

        mDialog = DirectoryChooserFragment.newInstance("DialogSample", null);

        prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        editor = prefs.edit();
        String settingDir = prefs.getString(Constants.SETTINGS_FAKKUDROID_FOLDER, "");
        if (!settingDir.isEmpty()) {
            EditText editText = (EditText) findViewById(R.id.etFolder);
            editText.setText(settingDir);
        } else {
            selectDefault(null);
        }
    }

    public void explore(View view) {
        mDialog.show(getFragmentManager(), null);
    }

    public void selectDefault(View view) {
        EditText editText = (EditText) findViewById(R.id.etFolder);
        editText.setText(Helper.getDefaultDir("", this).getAbsolutePath());
    }

    public void save(View view) {
        EditText editText = (EditText) findViewById(R.id.etFolder);
        String fakkuFolder = editText.getText().toString();

        //Validation folder
        File file = new File(fakkuFolder);
        if (!file.exists()&&!file.isDirectory()) {
            if (!file.mkdirs()) {
                Toast.makeText(this, R.string.error_creating_folder, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        File nomedia = new File(fakkuFolder, ".nomedia");
        boolean hasPermission = false;
        try {
            if(nomedia.exists()){
                nomedia.delete();
            }
            hasPermission = nomedia.createNewFile();
        } catch (IOException e) {
            hasPermission = false;
        }
        if(!hasPermission){
            Toast.makeText(this, R.string.error_write_permission, Toast.LENGTH_SHORT).show();
            return;
        }
        editor.putString(Constants.SETTINGS_FAKKUDROID_FOLDER, fakkuFolder);
        boolean directorySaved = editor.commit();
        if(!directorySaved){
            Toast.makeText(this, R.string.error_creating_folder, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Helper.getDownloadDir("", this).listFiles()!=null&&Helper.getDownloadDir("", this).listFiles().length > 0) {
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.detect_contents)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(SelectFolderActivity.this, ImporterActivity.class);
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
