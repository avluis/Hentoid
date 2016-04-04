package me.devsaki.hentoid.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.turhanoz.android.reactivedirectorychooser.event.OnDirectoryCancelEvent;
import com.turhanoz.android.reactivedirectorychooser.event.OnDirectoryChosenEvent;
import com.turhanoz.android.reactivedirectorychooser.ui.DirectoryChooserFragment;
import com.turhanoz.android.reactivedirectorychooser.ui.OnDirectoryChooserFragmentInteraction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.v2.bean.URLBean;

/**
 * Created by avluis on 04/02/2016.
 * Library Directory and Import Activity
 */
public class ImportActivity extends AppCompatActivity implements
        OnDirectoryChooserFragmentInteraction {
    private static final String TAG = ImportActivity.class.getName();

    private final static int STORAGE_PERMISSION_REQUEST = 1;
    private static final String resultKey = "resultKey";
    private String result = "RESULT_EMPTY";
    private File currentRootDirectory = Environment.getExternalStorageDirectory();
    private MaterialDialog dialog;

    public static String getResultKey() {
        return resultKey;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_transparent);

        pickDownloadFolder();
    }

    // Validate permissions
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void pickDownloadFolder() {
        if (ActivityCompat.checkSelfPermission(ImportActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(ImportActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
        } else {
            // We can present our directory picker
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            DialogFragment mDirectoryDialog = DirectoryChooserFragment.
                    newInstance(currentRootDirectory);
            mDirectoryDialog.show(transaction, "RDC");
        }
    }

    @Override
    public void onBackPressed() {
        // Send result back to activity
        result = "RESULT_CANCELED";
        Intent returnIntent = new Intent();
        returnIntent.putExtra(resultKey, result);
        setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    @Override
    public void onEvent(OnDirectoryChosenEvent event) {
        currentRootDirectory = event.getFile();

        validateFolder(currentRootDirectory);

    }

    @Override
    public void onEvent(OnDirectoryCancelEvent event) {
        onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                pickDownloadFolder();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    result = "PERMISSION_DENIED";
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(resultKey, result);
                    setResult(RESULT_CANCELED, returnIntent);
                    finish();
                } else {
                    result = "PERMISSION_DENIED_FORCED";
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(resultKey, result);
                    setResult(RESULT_CANCELED, returnIntent);
                    finish();
                }
            }
        }
    }

    private void validateFolder(File folder) {
        String hentoidFolder = folder.getAbsolutePath();
        SharedPreferences prefs = HentoidApplication.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();

        // Validate folder
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
                boolean deleted = nomedia.delete();
                if (deleted) {
                    System.out.println(".nomedia file deleted");
                }
            }
            hasPermission = nomedia.createNewFile();
        } catch (IOException e) {
            hasPermission = false;
        }

        if (!hasPermission) {
            Toast.makeText(this, R.string.error_write_permission, Toast.LENGTH_SHORT).show();
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
            downloadDirs.add(AndroidHelper.getDownloadDir(s, this));
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
                    .setMessage(R.string.contents_detected)
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Prior Library found, send results to scan
                                    System.out.println("Scanning files...");
                                    // TODO: Send results to importer
                                }

                            })
                    .setNegativeButton(android.R.string.no,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Prior Library found, but user chose to cancel
                                    result = "EXISTING_LIBRARY_FOUND";
                                    Intent returnIntent = new Intent();
                                    returnIntent.putExtra(resultKey, result);
                                    setResult(RESULT_CANCELED, returnIntent);
                                    finish();
                                }

                            })
                    .show();
        } else {
            // New Library!
            Handler handler = new Handler();
            result = "NEW_LIBRARY_CREATED";

            handler.postDelayed(new Runnable() {

                public void run() {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(resultKey, result);
                    setResult(RESULT_OK, returnIntent);
                    finish();
                }
            }, 100);
        }
    }

    private class ImportAsyncTask extends AsyncTask<Integer, String, List<Content>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            new MaterialDialog.Builder(getApplicationContext())
                    .title(R.string.progress_dialog)
                    .content(R.string.please_wait)
                    .contentGravity(GravityEnum.CENTER)
                    .progress(false, 150, true)
                    .cancelable(false)
                    .showListener(new DialogInterface.OnShowListener() {
                        @Override
                        public void onShow(DialogInterface dialogInterface) {
                            dialog = (MaterialDialog) dialogInterface;
                            // TODO: Hook to dialog here
                        }
                    }).show();

        }

        @Override
        protected List<Content> doInBackground(Integer... params) {
            return null;
        }
    }

    private List<Attribute> from(List<URLBean> urlBeans,
                                 @SuppressWarnings("SameParameterValue") AttributeType type) {
        List<Attribute> attributes = null;
        if (urlBeans == null) {
            return null;
        }
        if (urlBeans.size() > 0) {
            attributes = new ArrayList<>();
            for (URLBean urlBean : urlBeans) {
                Attribute attribute = from(urlBean, type);
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }
        }

        return attributes;
    }

    private Attribute from(URLBean urlBean, AttributeType type) {
        if (urlBean == null) {
            return null;
        }
        try {
            if (urlBean.getDescription() == null) {
                throw new RuntimeException("Problems loading attribute v2.");
            }

            return new Attribute()
                    .setName(urlBean.getDescription())
                    .setUrl(urlBean.getId())
                    .setType(type);
        } catch (Exception ex) {
            Log.e(TAG, "Parsing urlBean to attribute", ex);
            return null;
        }
    }
}