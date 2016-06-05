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
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

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

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ContentV1;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.model.DoujinBuilder;
import me.devsaki.hentoid.model.URLBuilder;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 04/02/2016.
 * Library Directory selection and Import Activity
 */
public class ImportActivity extends BaseActivity implements
        OnDirectoryChooserFragmentInteraction {
    private static final String TAG = LogHelper.makeLogTag(ImportActivity.class);

    private static final String DIR_KEY = "currentDir";
    private AlertDialog mAddDialog;
    private String result;
    private final Handler mImportHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 0) {
                cleanUp(mAddDialog);
            }
            mImportHandler.removeCallbacksAndMessages(null);
            return false;
        }
    });
    private File currentRootDirectory;
    private DirectoryChooserFragment mDirectoryDialog;
    private HentoidDB db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout relativeLayout = new RelativeLayout(this, null, R.style.ImportTheme);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(relativeLayout, layoutParams);

        db = HentoidDB.getInstance(this);

        mAddDialog = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_dialog_warning)
                .setTitle(R.string.add_dialog)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .create();

        initImport(savedInstanceState);
    }

    private void initImport(Bundle savedState) {
        if (savedState == null) {
            result = ConstsImport.RESULT_EMPTY;
        } else {
            currentRootDirectory = (File) savedState.getSerializable(DIR_KEY);
            result = savedState.getString(ConstsImport.RESULT_KEY);
        }
        checkForDefaultDirectory();
    }

    private void checkForDefaultDirectory() {
        if (checkPermissions()) {
            File file = new File(Environment.getExternalStorageDirectory() +
                    "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
            if (file.exists() && file.isDirectory()) {
                LogHelper.d(TAG, "Default Directory Found.");
                currentRootDirectory = file;
            } else {
                currentRootDirectory = Helper.getDefaultDir(this, "");
            }
            pickDownloadDirectory();
        } else {
            LogHelper.d(TAG, "Do we have permission?");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(DIR_KEY, currentRootDirectory);
        outState.putString(ConstsImport.RESULT_KEY, result);
        super.onSaveInstanceState(outState);
    }

    // Validate permissions
    private boolean checkPermissions() {
        if (Helper.permissionsCheck(
                ImportActivity.this, ConstsImport.RQST_STORAGE_PERMISSION)) {
            LogHelper.d(TAG, "Storage permission allowed!");
            return true;
        } else {
            LogHelper.d(TAG, "Storage permission denied!");
        }
        return false;
    }

    // Present Directory Picker
    private void pickDownloadDirectory() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        mDirectoryDialog = DirectoryChooserFragment.
                newInstance(currentRootDirectory);
        mDirectoryDialog.show(transaction, "RDC");
    }

    @Override
    public void onBackPressed() {
        // Send result back to activity
        result = ConstsImport.RESULT_CANCELED;
        LogHelper.d(TAG, result);
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
        setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    @Override
    public void onEvent(OnDirectoryChosenEvent event) {
        currentRootDirectory = event.getFile();
        LogHelper.d(TAG, "Storage Path: " + currentRootDirectory);
        mDirectoryDialog.dismiss();
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
                result = ConstsImport.PERMISSION_GRANTED;
                Intent returnIntent = new Intent();
                returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                setResult(RESULT_OK, returnIntent);
                finish();
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // Permission Denied
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    result = ConstsImport.PERMISSION_DENIED;
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                    setResult(RESULT_CANCELED, returnIntent);
                    finish();
                } else {
                    result = ConstsImport.PERMISSION_DENIED_FORCED;
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                    setResult(RESULT_CANCELED, returnIntent);
                    finish();
                }
            }
        }
    }

    private void validateFolder(File folder) {
        String hentoidFolder = folder.getAbsolutePath();
        SharedPreferences prefs = HentoidApp.getSharedPrefs();
        SharedPreferences.Editor editor = prefs.edit();

        // Validate folder
        File file = new File(hentoidFolder);
        if (!file.exists() && !file.isDirectory()) {
            if (!file.mkdirs()) {
                Helper.toast(this, R.string.error_creating_folder);
                return;
            }
        }

        File nomedia = new File(hentoidFolder, ".nomedia");
        boolean hasPermission;
        // Clean up (if any) nomedia file
        try {
            if (nomedia.exists()) {
                boolean deleted = nomedia.delete();
                if (deleted) {
                    LogHelper.d(TAG, ".nomedia file deleted");
                }
            }
            // Re-create nomedia file to confirm write permissions
            hasPermission = nomedia.createNewFile();
        } catch (IOException e) {
            hasPermission = false;
            HentoidApp.getInstance().trackException(e);
            LogHelper.e(TAG, "We couldn't confirm write permissions to this location: ", e);
        }

        if (!hasPermission) {
            Helper.toast(this, R.string.error_write_permission);
            return;
        }

        editor.putString(Consts.SETTINGS_FOLDER, hentoidFolder);

        boolean directorySaved = editor.commit();
        if (!directorySaved) {
            Helper.toast(this, R.string.error_creating_folder);
            return;
        }

        List<File> downloadDirs = new ArrayList<>();
        for (Site s : Site.values()) {
            downloadDirs.add(Helper.getSiteDownloadDir(this, s));
        }

        List<File> files = new ArrayList<>();
        for (File downloadDir : downloadDirs) {
            File[] contentFiles = downloadDir.listFiles();
            if (contentFiles != null)
                files.addAll(Arrays.asList(contentFiles));
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_dialog_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.contents_detected)
                .setPositiveButton(android.R.string.yes,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                // Prior Library found, drop and recreate db
                                cleanUpDB();

                                // Send results to scan
                                Helper.executeAsyncTask(new ImportAsyncTask());
                            }

                        })
                .setNegativeButton(android.R.string.no,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                // Prior Library found, but user chose to cancel
                                result = ConstsImport.EXISTING_LIBRARY_FOUND;
                                Intent returnIntent = new Intent();
                                returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                                setResult(RESULT_CANCELED, returnIntent);
                                finish();
                            }

                        })
                .create();
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        if (files.size() > 0) {
            dialog.show();
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            cleanUpDB();
            result = ConstsImport.NEW_LIBRARY_CREATED;

            Handler handler = new Handler();

            LogHelper.d(TAG, result);

            handler.postDelayed(new Runnable() {

                public void run() {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                    setResult(RESULT_OK, returnIntent);
                    finish();
                }
            }, 100);
        }
    }

    private void cleanUpDB() {
        Context context = HentoidApp.getAppContext();
        context.deleteDatabase(Consts.DATABASE_NAME);
        LogHelper.d(TAG, R.string.cleaning_up_db);
    }

    private void cleanUp(AlertDialog mAddDialog) {
        if (mAddDialog != null) {
            mAddDialog.dismiss();
        }
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
        setResult(RESULT_OK, returnIntent);
        finish();
    }

    private void finishImport(final List<Content> contents) {
        if (contents != null && contents.size() > 0) {
            LogHelper.d(TAG, "Adding contents to db.");
            mAddDialog.show();

            Thread thread = new Thread() {
                @Override
                public void run() {
                    // Grab all parsed content and add to database
                    db.insertContents(contents.toArray(new Content[contents.size()]));
                    mImportHandler.sendEmptyMessage(0);
                }
            };
            thread.start();

            result = ConstsImport.EXISTING_LIBRARY_IMPORTED;
        } else {
            result = ConstsImport.NEW_LIBRARY_CREATED;
            cleanUp(mAddDialog);
        }
    }

    private List<Attribute> from(List<URLBuilder> urlBuilders, AttributeType type) {
        List<Attribute> attributes = null;
        if (urlBuilders == null) {
            return null;
        }
        if (urlBuilders.size() > 0) {
            attributes = new ArrayList<>();
            for (URLBuilder urlBuilder : urlBuilders) {
                Attribute attribute = from(urlBuilder, type);
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }
        }

        return attributes;
    }

    private Attribute from(URLBuilder urlBuilder, AttributeType type) {
        if (urlBuilder == null) {
            return null;
        }
        try {
            if (urlBuilder.getDescription() == null) {
                throw new RuntimeException("Problems loading attribute v2.");
            }

            return new Attribute()
                    .setName(urlBuilder.getDescription())
                    .setUrl(urlBuilder.getId())
                    .setType(type);
        } catch (Exception e) {
            LogHelper.e(TAG, "Parsing URL to attribute: ", e);
            return null;
        }
    }

    private class ImportAsyncTask extends AsyncTask<Integer, String, List<Content>> {
        private MaterialDialog mImportDialog;
        private List<File> downloadDirs;
        private List<File> files;
        private List<Content> contents;
        private int currentPercent;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            final MaterialDialog mScanDialog =
                    new MaterialDialog.Builder(ImportActivity.this)
                            .title(R.string.import_dialog)
                            .content(R.string.please_wait)
                            .contentGravity(GravityEnum.CENTER)
                            .progress(false, 100, false)
                            .cancelable(false)
                            .showListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(DialogInterface dialogInterface) {
                                    mImportDialog = (MaterialDialog) dialogInterface;
                                }
                            }).build();
            mScanDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            downloadDirs = new ArrayList<>();
            for (Site site : Site.values()) {
                // Grab all folders in site folders in storage directory
                downloadDirs.add(Helper.getSiteDownloadDir(ImportActivity.this, site));
            }

            files = new ArrayList<>();
            for (File downloadDir : downloadDirs) {
                // Grab all files in downloadDirs
                files.addAll(Arrays.asList(downloadDir.listFiles()));
            }

            mScanDialog.show();
        }

        @Override
        protected void onPostExecute(List<Content> contents) {
            mImportDialog.dismiss();
            finishImport(contents);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (currentPercent == 100) {
                mImportDialog.setContent(R.string.adding_to_db);
            } else {
                mImportDialog.setContent(R.string.scanning_files);
            }
            mImportDialog.setProgress(currentPercent);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected List<Content> doInBackground(Integer... params) {
            int processed = 0;
            if (files.size() > 0) {
                contents = new ArrayList<>();
                for (File file : files) {
                    if (file.isDirectory()) {
                        // (v2) JSON file format
                        File json = new File(file, Consts.JSON_FILE_NAME_V2);
                        if (json.exists()) {
                            try {
                                Content content = JsonHelper.jsonToObject(json, Content.class);
                                if (content.getStatus() != StatusContent.DOWNLOADED
                                        && content.getStatus() != StatusContent.ERROR) {
                                    content.setStatus(StatusContent.MIGRATED);
                                }
                                contents.add(content);
                            } catch (Exception e) {
                                LogHelper.e(TAG, "Error reading JSON (v2) file: ", e);
                            }
                        } else {
                            // (v1) JSON file format
                            json = new File(file, Consts.JSON_FILE_NAME);
                            if (json.exists()) {
                                try {
                                    //noinspection deprecation
                                    ContentV1 content = JsonHelper.jsonToObject(json, ContentV1.class);
                                    if (content.getStatus() != StatusContent.DOWNLOADED
                                            && content.getStatus() != StatusContent.ERROR) {
                                        content.setMigratedStatus();
                                    }
                                    Content contentV2 = content.toV2Content();
                                    try {
                                        JsonHelper.saveJson(contentV2, file);
                                    } catch (IOException e) {
                                        LogHelper.e(TAG, "Error converting JSON (v1) to JSON (v2): "
                                                + content.getTitle(), e);
                                    }
                                    contents.add(contentV2);
                                } catch (Exception e) {
                                    LogHelper.e(TAG, "Error reading JSON (v1) file: ", e);
                                }
                            } else {
                                // (old) JSON file format (legacy and/or FAKKUDroid App)
                                json = new File(file, Consts.OLD_JSON_FILE_NAME);
                                Date importedDate = new Date();
                                if (json.exists()) {
                                    try {
                                        DoujinBuilder doujinBuilder =
                                                JsonHelper.jsonToObject(json, DoujinBuilder.class);
                                        //noinspection deprecation
                                        ContentV1 content = new ContentV1();
                                        content.setUrl(doujinBuilder.getId());
                                        content.setHtmlDescription(doujinBuilder.getDescription());
                                        content.setTitle(doujinBuilder.getTitle());
                                        content.setSeries(from(doujinBuilder.getSeries(),
                                                AttributeType.SERIE));
                                        Attribute artist = from(doujinBuilder.getArtist(),
                                                AttributeType.ARTIST);
                                        List<Attribute> artists = null;
                                        if (artist != null) {
                                            artists = new ArrayList<>(1);
                                            artists.add(artist);
                                        }
                                        content.setArtists(artists);
                                        content.setCoverImageUrl(doujinBuilder.getUrlImageTitle());
                                        content.setQtyPages(doujinBuilder.getQtyPages());
                                        Attribute translator = from(doujinBuilder.getTranslator(),
                                                AttributeType.TRANSLATOR);
                                        List<Attribute> translators = null;
                                        if (translator != null) {
                                            translators = new ArrayList<>(1);
                                            translators.add(translator);
                                        }
                                        content.setTranslators(translators);
                                        content.setTags(from(doujinBuilder.getLstTags(),
                                                AttributeType.TAG));
                                        content.setLanguage(from(doujinBuilder.getLanguage(),
                                                AttributeType.LANGUAGE));

                                        content.setMigratedStatus();
                                        content.setDownloadDate(importedDate.getTime());
                                        Content contentV2 = content.toV2Content();
                                        try {
                                            JsonHelper.saveJson(contentV2, file);
                                        } catch (IOException e) {
                                            LogHelper.e(TAG,
                                                    "Error converting JSON (old) to JSON (v2): "
                                                            + content.getTitle(), e);
                                        }
                                        contents.add(contentV2);
                                    } catch (Exception e) {
                                        LogHelper.e(TAG, "Error reading JSON (old) file: ", e);
                                    }
                                }
                            }
                        }
                        publishProgress();
                    }
                    processed++;
                    currentPercent = (int) (processed * 100.0 / files.size());
                }
            }

            return contents;
        }
    }
}