package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.Subscribe;

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
import me.devsaki.hentoid.dirpicker.events.OnDirCancelEvent;
import me.devsaki.hentoid.dirpicker.events.OnDirChosenEvent;
import me.devsaki.hentoid.dirpicker.events.OnSAFRequestEvent;
import me.devsaki.hentoid.dirpicker.events.OnTextViewClickedEvent;
import me.devsaki.hentoid.dirpicker.events.OpFailedEvent;
import me.devsaki.hentoid.dirpicker.ui.DirChooserFragment;
import me.devsaki.hentoid.dirpicker.util.Convert;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.model.DoujinBuilder;
import me.devsaki.hentoid.model.URLBuilder;
import me.devsaki.hentoid.util.AttributeException;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.annimon.stream.Collectors.toList;

/**
 * Created by avluis on 04/02/2016.
 * Library Directory selection and Import Activity
 */
public class ImportActivity extends BaseActivity {

    private static final String CURRENT_DIR = "currentDir";
    private static final String PREV_DIR = "prevDir";
    private String result;
    private File currentRootDir;
    private File prevRootDir;
    private DirChooserFragment dirChooserFragment;
    private ImageView instImage;
    private boolean restartFlag;
    private boolean prefInit;
    private boolean defaultInit;

    private static List<Attribute> from(List<URLBuilder> urlBuilders, AttributeType type) {
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

    private static Attribute from(URLBuilder urlBuilder, AttributeType type) {
        if (urlBuilder == null) {
            return null;
        }
        try {
            if (urlBuilder.getDescription() == null) {
                throw new AttributeException("Problems loading attribute v2.");
            }

            return new Attribute()
                    .setName(urlBuilder.getDescription())
                    .setUrl(urlBuilder.getId())
                    .setType(type);
        } catch (Exception e) {
            Timber.e(e, "Parsing URL to attribute");
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View contentView = new View(this, null, R.style.ImportTheme);
        setContentView(contentView);

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(Intent.ACTION_APPLICATION_PREFERENCES)) {
                Timber.d("Running from prefs screen.");
                prefInit = true;
            }
            if (intent.getAction().equals(Intent.ACTION_GET_CONTENT)) {
                Timber.d("Importing default directory.");
                defaultInit = true;
            } else {
                Timber.d("Intent: %s Action: %s", intent, intent.getAction());
            }
        }
        prepImport(savedInstanceState);
    }

    private void prepImport(Bundle savedState) {
        if (savedState == null) {
            result = ConstsImport.RESULT_EMPTY;
        } else {
            currentRootDir = (File) savedState.getSerializable(CURRENT_DIR);
            prevRootDir = (File) savedState.getSerializable(PREV_DIR);
            result = savedState.getString(ConstsImport.RESULT_KEY);
        }
        checkForDefaultDirectory();
    }

    private void checkForDefaultDirectory() {
        if (checkPermissions()) {
            String settingDir = Preferences.getRootFolderName();
            Timber.d(settingDir);

            File file;
            if (!settingDir.isEmpty()) {
                file = new File(settingDir);
            } else {
                file = new File(Environment.getExternalStorageDirectory() +
                        "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
            }

            if (file.exists() && file.isDirectory()) {
                currentRootDir = file;
            } else {
                currentRootDir = FileHelper.getDefaultDir(this, "");
                Timber.d("Creating new storage directory.");
            }
            pickDownloadDirectory(currentRootDir);
        } else {
            Timber.d("Do we have permission?");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(CURRENT_DIR, currentRootDir);
        outState.putSerializable(PREV_DIR, prevRootDir);
        outState.putString(ConstsImport.RESULT_KEY, result);
        super.onSaveInstanceState(outState);
    }

    // Validate permissions
    private boolean checkPermissions() {
        if (Helper.permissionsCheck(
                ImportActivity.this, ConstsImport.RQST_STORAGE_PERMISSION, true)) {
            Timber.d("Storage permission allowed!");
            return true;
        } else {
            Timber.d("Storage permission denied!");
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length <= 0) return;

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

    // Present Directory Picker
    private void pickDownloadDirectory(File dir) {
        File downloadDir = dir;
        if (FileHelper.isOnExtSdCard(dir) && !FileHelper.isWritable(dir)) {
            Timber.d("Inaccessible: moving back to default directory.");
            downloadDir = currentRootDir = new File(Environment.getExternalStorageDirectory() +
                    "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
        }

        if (defaultInit) {
            prevRootDir = currentRootDir;
            initImport();
        } else {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            dirChooserFragment = DirChooserFragment.newInstance(downloadDir);
            dirChooserFragment.show(transaction, "DirectoryChooserFragment");
        }
    }

    @Override
    public void onBackPressed() {
        // Send result back to activity
        result = ConstsImport.RESULT_CANCELED;
        Timber.d(result);
        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
        setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    @Subscribe
    public void onDirCancel(OnDirCancelEvent event) {
        onBackPressed();
    }

    @Subscribe
    public void onDirChosen(OnDirChosenEvent event) {
        File chosenDir = event.getDir();
        prevRootDir = currentRootDir;

        if (!currentRootDir.equals(chosenDir)) {
            restartFlag = true;
            currentRootDir = chosenDir;
        }
        dirChooserFragment.dismiss();
        initImport();
    }

    private void initImport() {
        Timber.d("Clearing SAF");
        FileHelper.clearUri();

        if (Build.VERSION.SDK_INT >= KITKAT) {
            revokePermission();
        }

        Timber.d("Storage Path: %s", currentRootDir);
        importFolder(currentRootDir);
    }

    @Subscribe
    public void onOpFailed(OpFailedEvent event) {
        dirChooserFragment.dismiss();
        prepImport(null);
    }

    @Subscribe
    public void onManualInput(OnTextViewClickedEvent event) {
        if (event.getClickType()) {
            Timber.d("Resetting directory back to default.");
            currentRootDir = new File(Environment.getExternalStorageDirectory() +
                    "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/");
            dirChooserFragment.dismiss();
            pickDownloadDirectory(currentRootDir);
        } else {
            final EditText text = new EditText(this);
            int paddingPx = Convert.dpToPixel(this, 16);
            text.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            text.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
            text.setText(currentRootDir.toString());

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dir_path)
                    .setMessage(R.string.dir_path_inst)
                    .setView(text)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> {
                                Editable value = text.getText();
                                processManualInput(value);
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private void processManualInput(@NonNull Editable value) {
        String path = String.valueOf(value);
        if (!("").equals(path)) {
            File file = new File(path);
            if (file.exists() && file.isDirectory() && file.canWrite()) {
                Timber.d("Got a valid directory!");
                currentRootDir = file;
                dirChooserFragment.dismiss();
                pickDownloadDirectory(currentRootDir);
            } else {
                dirChooserFragment.dismiss();
                prepImport(null);
            }
        }
        Timber.d(path);
    }

    @Subscribe
    public void onSAFRequest(OnSAFRequestEvent event) {
        String[] externalDirs = FileHelper.getExtSdCardPaths();
        List<File> writeableDirs = new ArrayList<>();
        if (externalDirs.length > 0) {
            Timber.d("External Directory(ies): %s", Arrays.toString(externalDirs));
            for (String externalDir : externalDirs) {
                File file = new File(externalDir);
                Timber.d("Is %s write-able? %s", externalDir, FileHelper.isWritable(file));
                if (FileHelper.isWritable(file)) {
                    writeableDirs.add(file);
                }
            }
        }
        resolveDirs(externalDirs, writeableDirs);
    }

    private void resolveDirs(String[] externalDirs, List<File> writeableDirs) {
        if (writeableDirs.isEmpty()) {
            Timber.d("Received no write-able external directories.");
            if (Helper.isAtLeastAPI(LOLLIPOP)) {
                if (externalDirs.length > 0) {
                    Helper.toast("Attempting SAF");
                    requestWritePermission();
                } else {
                    noSDSupport();
                }
            } else {
                noSDSupport();
            }
        } else {
            if (writeableDirs.size() == 1) {
                // If we get exactly one write-able path returned, attempt to make use of it
                String sdDir = writeableDirs.get(0) + "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/";
                if (FileHelper.validateFolder(sdDir)) {
                    Timber.d("Got access to SD Card.");
                    currentRootDir = new File(sdDir);
                    dirChooserFragment.dismiss();
                    pickDownloadDirectory(currentRootDir);
                } else {
                    if (Build.VERSION.SDK_INT == KITKAT) {
                        Timber.d("Unable to write to SD Card.");
                        showKitkatRationale();
                    } else if (Helper.isAtLeastAPI(LOLLIPOP)) {
                        PackageManager manager = this.getPackageManager();
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        List<ResolveInfo> handlers = manager.queryIntentActivities(intent, 0);
                        if (handlers != null && handlers.size() > 0) {
                            Timber.d("Device should be able to handle the SAF request");
                            Helper.toast("Attempting SAF");
                            requestWritePermission();
                        } else {
                            Timber.d("No apps can handle the requested intent.");
                        }
                    } else {
                        noSDSupport();
                    }
                }
            } else {
                Timber.d("We got a fancy device here.");
                Timber.d("Available storage locations: %s", writeableDirs);
                noSDSupport();
            }
        }
    }

    private void showKitkatRationale() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.kitkat_rationale)
                .setTitle("Error!")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void noSDSupport() {
        Timber.d("No write-able directories :(");
        Helper.toast(R.string.no_sd_support);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        attachInstImage();
    }

    private void attachInstImage() {
        // A list of known devices can be used here to present instructions relevant to that device
        if (instImage != null) {
            instImage.setImageDrawable(ContextCompat.getDrawable(ImportActivity.this,
                    R.drawable.bg_sd_instructions));
        }
    }

    @RequiresApi(api = LOLLIPOP)
    private void requestWritePermission() {
        runOnUiThread(() -> {
            instImage = new ImageView(ImportActivity.this);
            attachInstImage();

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(ImportActivity.this)
                            .setTitle("Requesting Write Permissions")
                            .setView(instImage)
                            .setPositiveButton(android.R.string.ok,
                                    (dialogInterface, i) -> {
                                        dialogInterface.dismiss();
                                        newSAFIntent();
                                    });
            final AlertDialog dialog = builder.create();
            instImage.setOnClickListener(v -> {
                dialog.dismiss();
                newSAFIntent();
            });
            dialog.show();
        });
    }

    @RequiresApi(api = LOLLIPOP)
    private void newSAFIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.M)) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Write Permission");
        }
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        startActivityForResult(intent, ConstsImport.RQST_STORAGE_PERMISSION);
    }

    @RequiresApi(api = KITKAT)
    private void revokePermission() {
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            getContentResolver().releasePersistableUriPermission(p.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        if (getContentResolver().getPersistedUriPermissions().size() == 0) {
            Timber.d("Permissions revoked successfully.");
        } else {
            Timber.d("Permissions failed to be revoked.");
        }
    }

    @RequiresApi(api = KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ConstsImport.RQST_STORAGE_PERMISSION && resultCode == RESULT_OK) {
            // Get Uri from Storage Access Framework
            Uri treeUri = data.getData();

            // Persist URI in shared preference so that you can use it later
            FileHelper.saveUri(treeUri);

            // Persist access permissions
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            dirChooserFragment.dismiss();

            if (FileHelper.getExtSdCardPaths().length > 0) {
                String[] paths = FileHelper.getExtSdCardPaths();
                String[] uriContents = treeUri.getPath().split(":");
                String folderStr = paths[0] + "/" + ((uriContents.length > 1)?(uriContents[1]+"/"):"") +Consts.DEFAULT_LOCAL_DIRECTORY;

                File folder = new File(folderStr);
                Timber.d("Directory created successfully: %s", FileHelper.createDirectory(folder));

                importFolder(folder);
            }
        }
    }

    private void importFolder(File folder) {
        if (!FileHelper.validateFolder(folder.getAbsolutePath(), true)) {
            prepImport(null);
            return;
        }

        List<File> downloadDirs = new ArrayList<>();
        for (Site s : Site.values()) {
            downloadDirs.add(FileHelper.getSiteDownloadDir(this, s));
        }

        List<File> files = new ArrayList<>();
        for (File downloadDir : downloadDirs) {
            File[] contentFiles = downloadDir.listFiles();
            if (contentFiles != null)
                files.addAll(Arrays.asList(contentFiles));
        }

        if (files.size() > 0) {
            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_dialog_warning)
                    .setCancelable(false)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.contents_detected)
                    .setPositiveButton(android.R.string.yes,
                            (dialog1, which) -> {
                                dialog1.dismiss();
                                // Prior Library found, drop and recreate db
                                cleanUpDB();
                                // Send results to scan
                                Helper.executeAsyncTask(new ImportAsyncTask(this));
                            })
                    .setNegativeButton(android.R.string.no,
                            (dialog12, which) -> {
                                dialog12.dismiss();
                                // Prior Library found, but user chose to cancel
                                restartFlag = false;
                                if (prevRootDir != null) {
                                    currentRootDir = prevRootDir;
                                }
                                if (currentRootDir != null) {
                                    FileHelper.validateFolder(currentRootDir.getAbsolutePath());
                                }
                                Timber.d("Restart needed: " + false);

                                result = ConstsImport.EXISTING_LIBRARY_FOUND;
                                Intent returnIntent = new Intent();
                                returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                                setResult(RESULT_CANCELED, returnIntent);
                                finish();
                            })
                    .create()
                    .show();
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            cleanUpDB();
            result = ConstsImport.NEW_LIBRARY_CREATED;

            Handler handler = new Handler();

            Timber.d(result);

            handler.postDelayed(() -> {
                Intent returnIntent = new Intent();
                returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
                setResult(RESULT_OK, returnIntent);
                finish();
            }, 100);
        }
    }

    private void cleanUpDB() {
        Timber.d("Cleaning up DB.");
        Context context = HentoidApp.getAppContext();
        context.deleteDatabase(Consts.DATABASE_NAME);
    }

    private void cleanUp() {
        Timber.d("Restart needed: %s", restartFlag);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, result);
        setResult(RESULT_OK, returnIntent);
        finish();

        if (restartFlag && prefInit) {
            Helper.doRestart(this);
        }
    }

    private class ImportAsyncTask extends AsyncTask<Integer, Integer, List<Content>> {

        private final ProgressDialog progressDialog;
        private final AlertDialog finishDialog;
        private final List<File> files;

        private ImportAsyncTask(Context context) {
            progressDialog = new ProgressDialog(context);
            progressDialog.setTitle(R.string.import_dialog);
            progressDialog.setMessage(context.getText(R.string.please_wait));
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);

            finishDialog = new AlertDialog.Builder(context)
                    .setIcon(R.drawable.ic_dialog_warning)
                    .setTitle(R.string.add_dialog)
                    .setMessage(R.string.please_wait)
                    .setCancelable(false)
                    .create();

            files = Stream.of(Site.values())
                    .map(site -> FileHelper.getSiteDownloadDir(context, site))
                    .map(File::listFiles)
                    .flatMap(Stream::of)
                    .filter(File::isDirectory)
                    .collect(toList());
        }

        @Override
        protected void onPreExecute() {
            progressDialog.show();
        }

        @Override
        protected void onPostExecute(List<Content> contents) {
            if (contents != null && contents.size() > 0) {
                result = ConstsImport.EXISTING_LIBRARY_IMPORTED;
            } else {
                result = ConstsImport.NEW_LIBRARY_CREATED;
            }
            cleanUp();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values[0] >= 0) {
                progressDialog.setProgress(values[0]);
            } else if (values[0] == -1) {
                progressDialog.dismiss();
                finishDialog.show();
            } else if (values[0] == -2) {
                finishDialog.dismiss();
            }
        }

        @Override
        protected List<Content> doInBackground(Integer... params) {
            List<Content> contents = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                // (v2) JSON file format
                File json = new File(file, Consts.JSON_FILE_NAME_V2);
                if (json.exists()) {
                    Content content = importJsonV2(json);
                    if (content != null) contents.add(content);
                } else {
                    // (v1) JSON file format
                    json = new File(file, Consts.JSON_FILE_NAME);
                    if (json.exists()) {
                        Content content = importJsonV1(json, file);
                        if (content != null) contents.add(content);
                    } else {
                        // (old) JSON file format (legacy and/or FAKKUDroid App)
                        json = new File(file, Consts.OLD_JSON_FILE_NAME);
                        if (json.exists()) {
                            Content content = importJsonLegacy(json, file);
                            if (content != null) contents.add(content);
                        }
                    }
                }
                publishProgress((int) (i * 100.0 / files.size()));
            }

            Timber.d("Adding contents to db.");

            publishProgress(-1);
            HentoidDB.getInstance(ImportActivity.this)
                    .insertContents(contents.toArray(new Content[contents.size()]));
            publishProgress(-2);

            return contents;
        }

        @Nullable
        @CheckResult
        private Content importJsonLegacy(File json, File file) {
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
                content.setDownloadDate(new Date().getTime());
                Content contentV2 = content.toV2Content();

                String fileRoot = Preferences.getRootFolderName();
                contentV2.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));
                try {
                    JsonHelper.saveJson(contentV2, file);
                } catch (IOException e) {
                    Timber.e(e,
                            "Error converting JSON (old) to JSON (v2): %s", content.getTitle());
                }

                return contentV2;
            } catch (Exception e) {
                Timber.e(e, "Error reading JSON (old) file");
            }
            return null;
        }

        @Nullable
        @CheckResult
        private Content importJsonV1(File json, File file) {
            try {
                //noinspection deprecation
                ContentV1 content = JsonHelper.jsonToObject(json, ContentV1.class);
                if (content.getStatus() != StatusContent.DOWNLOADED
                        && content.getStatus() != StatusContent.ERROR) {
                    content.setMigratedStatus();
                }
                Content contentV2 = content.toV2Content();

                String fileRoot = Preferences.getRootFolderName();
                contentV2.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));
                try {
                    JsonHelper.saveJson(contentV2, file);
                } catch (IOException e) {
                    Timber.e(e, "Error converting JSON (v1) to JSON (v2): %s", content.getTitle());
                }

                return contentV2;
            } catch (Exception e) {
                Timber.e(e, "Error reading JSON (v1) file");
            }
            return null;
        }

        @Nullable
        @CheckResult
        private Content importJsonV2(File json) {
            try {
                Content content = JsonHelper.jsonToObject(json, Content.class);

                if (null == content.getAuthor()) content.populateAuthor();

                String fileRoot = Preferences.getRootFolderName();
                content.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));

                if (content.getStatus() != StatusContent.DOWNLOADED
                        && content.getStatus() != StatusContent.ERROR) {
                    content.setStatus(StatusContent.MIGRATED);
                }

                return content;
            } catch (Exception e) {
                Timber.e(e, "Error reading JSON (v2) file");
            }
            return null;
        }
    }
}
