package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
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
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ContentV1;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AndroidHelper;
import me.devsaki.hentoid.util.Constants;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.v2.bean.DoujinBean;
import me.devsaki.hentoid.v2.bean.URLBean;

/**
 * Created by avluis on 04/02/2016.
 * Library Directory selection and Import Activity
 */
public class ImportActivity extends AppCompatActivity implements
        OnDirectoryChooserFragmentInteraction {
    private static final String TAG = LogHelper.makeLogTag(ImportActivity.class);

    private final static int STORAGE_PERMISSION_REQUEST = 1;
    private static final String resultKey = "resultKey";
    private String result = "RESULT_EMPTY";
    private File currentRootDirectory;

    public static String getResultKey() {
        return resultKey;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout relativeLayout = new RelativeLayout(this, null, R.style.ImportTheme);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(relativeLayout, layoutParams);

        initImport(savedInstanceState);
    }

    private void initImport(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            checkPermissions();
            currentRootDirectory = Environment.getExternalStorageDirectory();
        } else {
            currentRootDirectory = (File) savedInstanceState.getSerializable("currentRootDirectory");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("currentRootDirectory", currentRootDirectory);
        super.onSaveInstanceState(outState);
    }

    // Validate permissions
    private void checkPermissions() {
        if (AndroidHelper.permissionsCheck(ImportActivity.this,
                STORAGE_PERMISSION_REQUEST)) {
            LogHelper.d(TAG, "Storage permission allowed!");
            pickDownloadDirectory();
        } else {
            LogHelper.d(TAG, "Storage permission denied!");
        }
    }

    // Present Directory Picker
    private void pickDownloadDirectory() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        DialogFragment mDirectoryDialog = DirectoryChooserFragment.
                newInstance(currentRootDirectory);
        mDirectoryDialog.show(transaction, "RDC");
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                new Handler().post(new Runnable() {

                    @Override
                    public void run() {
                        Intent intent = getIntent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        overridePendingTransition(0, 0);
                        finish();

                        overridePendingTransition(0, 0);
                        startActivity(intent);
                    }
                });
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
                    LogHelper.d(TAG, ".nomedia file deleted");
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
                    .setCancelable(false)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.contents_detected)
                    .setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Prior Library found, drop and recreate db
                                    createNewLibrary();

                                    // Send results to scan
                                    AndroidHelper.executeAsyncTask(new ImportAsyncTask());
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
            // New library created - drop and recreate db
            createNewLibrary();
            result = "NEW_LIBRARY_CREATED";

            Handler handler = new Handler();

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

    private void createNewLibrary() {
        Context context = getApplicationContext();
        context.deleteDatabase(Constants.DATABASE_NAME);
    }

    @SuppressWarnings("SameParameterValue")
    private List<Attribute> from(List<URLBean> urlBeans, AttributeType type) {
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
        } catch (Exception e) {
            LogHelper.e(TAG, "Parsing urlBean to attribute: ", e);
            return null;
        }
    }

    private class ImportAsyncTask extends AsyncTask<Integer, String, List<Content>> {
        private MaterialDialog mDialog;
        private MaterialDialog.Builder mDialogBuilder;
        private List<File> downloadDirs;
        private List<File> files;
        private List<Content> contents;
        private HentoidDB hentoidDB;
        private int currentPercent;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            hentoidDB = new HentoidDB(ImportActivity.this);
            downloadDirs = new ArrayList<>();

            for (Site site : Site.values()) {
                // Grab all folders in site folders in storage directory
                downloadDirs.add(AndroidHelper.getDownloadDir(site, ImportActivity.this));
            }

            files = new ArrayList<>();

            for (File downloadDir : downloadDirs) {
                // Grab all files in downloadDirs
                files.addAll(Arrays.asList(downloadDir.listFiles()));
            }

            mDialogBuilder = new MaterialDialog.Builder(ImportActivity.this)
                    .title(R.string.progress_dialog)
                    .content(R.string.please_wait)
                    .contentGravity(GravityEnum.CENTER)
                    .progress(false, 100, false)
                    .cancelable(false)
                    .showListener(new DialogInterface.OnShowListener() {
                        @Override
                        public void onShow(DialogInterface dialogInterface) {
                            mDialog = (MaterialDialog) dialogInterface;
                        }
                    });

            mDialogBuilder.show();
        }

        @Override
        protected void onPostExecute(List<Content> contents) {
            if (contents != null && contents.size() > 0) {
                // Grab all parsed content and add to database
                hentoidDB.insertContents(contents.toArray(new Content[contents.size()]));
                result = "EXISTING_LIBRARY_IMPORTED";
            } else {
                result = "NEW_LIBRARY_CREATED";
            }
            mDialog.dismiss();

            Handler handler = new Handler();

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(resultKey, result);
                    setResult(RESULT_OK, returnIntent);
                    finish();
                }
            }, 100);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (currentPercent == 100) {
                mDialog.setContent(R.string.adding_to_db);
            } else {
                mDialog.setContent(R.string.scanning_files);
            }
            mDialog.setProgress(currentPercent);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected List<Content> doInBackground(Integer... params) {
            int processed = 0;
            if (files.size() > 0) {
                contents = new ArrayList<>();
                Date importedDate = new Date();
                for (File file : files) {
                    processed++;
                    currentPercent = (int) (processed * 100.0 / files.size());
                    if (file.isDirectory()) {
                        publishProgress(file.getName());
                        File json = new File(file, Constants.JSON_FILE_NAME_V2);
                        if (json.exists()) {
                            try {
                                Content content = Helper.jsonToObject(json, Content.class);
                                if (content.getStatus() != StatusContent.DOWNLOADED
                                        && content.getStatus() != StatusContent.ERROR) {
                                    content.setStatus(StatusContent.MIGRATED);
                                }
                                contents.add(content);
                            } catch (Exception e) {
                                LogHelper.e(TAG, "Reading JSON file: ", e);
                            }
                        } else {
                            json = new File(file, Constants.JSON_FILE_NAME);
                            if (json.exists()) {
                                try {
                                    ContentV1 content = Helper.jsonToObject(json, ContentV1.class);
                                    if (content.getStatus() != StatusContent.DOWNLOADED
                                            && content.getStatus() != StatusContent.ERROR) {
                                        content.setMigratedStatus();
                                    }
                                    Content contentV2 = content.toContent();
                                    try {
                                        Helper.saveJson(contentV2, file);
                                    } catch (IOException e) {
                                        LogHelper.e(TAG, "Error Save JSON " + content.getTitle(), e);
                                    }
                                    contents.add(contentV2);
                                } catch (Exception e) {
                                    LogHelper.e(TAG, "Reading JSON file: ", e);
                                }
                            } else {
                                json = new File(file, Constants.OLD_JSON_FILE_NAME);
                                if (json.exists()) {
                                    try {
                                        DoujinBean doujinBean =
                                                Helper.jsonToObject(json, DoujinBean.class);
                                        ContentV1 content = new ContentV1();
                                        content.setUrl(doujinBean.getId());
                                        content.setHtmlDescription(doujinBean.getDescription());
                                        content.setTitle(doujinBean.getTitle());
                                        content.setSeries(from(doujinBean.getSeries(),
                                                AttributeType.SERIE));
                                        Attribute artist = from(doujinBean.getArtist(),
                                                AttributeType.ARTIST);
                                        List<Attribute> artists = null;
                                        if (artist != null) {
                                            artists = new ArrayList<>(1);
                                            artists.add(artist);
                                        }
                                        content.setArtists(artists);
                                        content.setCoverImageUrl(doujinBean.getUrlImageTitle());
                                        content.setQtyPages(doujinBean.getQtyPages());
                                        Attribute translator = from(doujinBean.getTranslator(),
                                                AttributeType.TRANSLATOR);
                                        List<Attribute> translators = null;
                                        if (translator != null) {
                                            translators = new ArrayList<>(1);
                                            translators.add(translator);
                                        }
                                        content.setTranslators(translators);
                                        content.setTags(from(doujinBean.getLstTags(),
                                                AttributeType.TAG));
                                        content.setLanguage(from(doujinBean.getLanguage(),
                                                AttributeType.LANGUAGE));

                                        content.setMigratedStatus();
                                        content.setDownloadDate(importedDate.getTime());
                                        Content contentV2 = content.toContent();
                                        try {
                                            Helper.saveJson(contentV2, file);
                                        } catch (IOException e) {
                                            LogHelper.e(TAG, "Error Save JSON " + content.getTitle(), e);
                                        }
                                        contents.add(contentV2);
                                    } catch (Exception e) {
                                        LogHelper.e(TAG, "Reading JSON file v2: ", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return contents;
        }
    }
}