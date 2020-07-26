package me.devsaki.hentoid.fragments.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.annimon.stream.Optional;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.threeten.bp.Instant;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

/**
 * Created by Robb on 05/2020
 * Dialog for the library metadata import feature
 */
public class LibImportDialogFragment extends DialogFragment {

    private static int RQST_PICK_IMPORT_FILE = 4;

    @IntDef({Result.OK, Result.CANCELED, Result.INVALID_FOLDER, Result.OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Result {
        int OK = 0;
        int CANCELED = 1;
        int INVALID_FOLDER = 2;
        int OTHER = 3;
    }

    // UI
    private ViewGroup rootView;
    private View selectFileBtn;
    private TextView progressTxt;
    private ProgressBar progressBar;
    private CheckBox libraryChk;
    private CheckBox queueChk;
    private View runBtn;

    // Variable used during the selection process
    private Uri selectedFileUri;

    // Variable used during the import process
    private int totalBooks;
    private int currentProgress;
    private int nbSuccess;
    private int queueSize;
    private Map<Site, DocumentFile> siteFoldersCache = null;
    private Map<Site, List<DocumentFile>> bookFoldersCache = new EnumMap<>(Site.class);

    // Disposable for RxJava
    private Disposable importDisposable = Disposables.empty();


    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        LibImportDialogFragment fragment = new LibImportDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_prefs_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (rootView instanceof ViewGroup) this.rootView = (ViewGroup) rootView;

        progressTxt = requireViewById(rootView, R.id.import_progress_text);
        progressBar = requireViewById(rootView, R.id.import_progress_bar);

        selectFileBtn = requireViewById(rootView, R.id.import_select_file_btn);
        selectFileBtn.setOnClickListener(v -> askFile());
    }

    private void askFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(JsonHelper.JSON_MIME_TYPE);
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        HentoidApp.LifeCycleListener.disable(); // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        startActivityForResult(intent, RQST_PICK_IMPORT_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        HentoidApp.LifeCycleListener.enable(); // Restores autolock on app going to background

        @Result int result = processPickerResult(requestCode, resultCode, data);
        switch (result) {
            case Result.OK:
                // File selected
                DocumentFile doc = DocumentFile.fromSingleUri(requireContext(), selectedFileUri);
                if (null == doc) return;
                selectFileBtn.setVisibility(View.GONE);
                checkFile(doc);
                break;
            case Result.CANCELED:
                Snackbar.make(rootView, R.string.import_canceled, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case Result.INVALID_FOLDER:
                Snackbar.make(rootView, R.string.import_invalid, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case Result.OTHER:
                Snackbar.make(rootView, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            default:
                // Nothing should happen here
        }
    }

    private @Result
    int processPickerResult(
            int requestCode,
            int resultCode,
            final Intent data) {
        HentoidApp.LifeCycleListener.enable(); // Restores autolock on app going to background

        // Return from the SAF picker
        if (requestCode == RQST_PICK_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            // Get Uri from Storage Access Framework
            Uri fileUri = data.getData();
            if (fileUri != null) {
                selectedFileUri = fileUri;
                return Result.OK;
            } else return Result.INVALID_FOLDER;
        } else if (resultCode == Activity.RESULT_CANCELED) {
            return Result.CANCELED;
        } else return Result.OTHER;
    }

    private void checkFile(@NonNull DocumentFile jsonFile) {
        // TODO display an indefinite progress bar just in case ?
        importDisposable = Single.fromCallable(() -> deserialiseJson(jsonFile))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        c -> onFileDeserialized(c, jsonFile),
                        Timber::w
                );
    }

    private void onFileDeserialized(Optional<JsonContentCollection> collectionOptional, DocumentFile jsonFile) {
        importDisposable.dispose();

        TextView errorTxt = requireViewById(rootView, R.id.import_file_invalid_text);
        if (collectionOptional.isEmpty()) {
            errorTxt.setText(getResources().getString(R.string.import_file_invalid, jsonFile.getName()));
            errorTxt.setVisibility(View.VISIBLE);
        } else {
            selectFileBtn.setVisibility(View.GONE);
            errorTxt.setVisibility(View.GONE);

            JsonContentCollection collection = collectionOptional.get();
            libraryChk = requireViewById(rootView, R.id.import_file_library_chk);
            int librarySize = collection.getLibrary().size();
            if (librarySize > 0) {
                libraryChk.setText(getResources().getQuantityString(R.plurals.import_file_library, librarySize, librarySize));
                libraryChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                libraryChk.setVisibility(View.VISIBLE);
            }
            queueChk = requireViewById(rootView, R.id.import_file_queue_chk);
            int mQueueSize = collection.getQueue().size();
            if (mQueueSize > 0) {
                queueChk.setText(getResources().getQuantityString(R.plurals.import_file_queue, mQueueSize, mQueueSize));
                queueChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                queueChk.setVisibility(View.VISIBLE);
            }
            requireViewById(rootView, R.id.import_warning_img).setVisibility(View.VISIBLE);
            requireViewById(rootView, R.id.import_file_help_text).setVisibility(View.VISIBLE);
            runBtn = requireViewById(rootView, R.id.import_run_btn);
            runBtn.setVisibility(View.VISIBLE);
            runBtn.setEnabled(false);

            RadioButton addChk = requireViewById(rootView, R.id.import_mode_add);
            runBtn.setOnClickListener(v -> runImport(collection, addChk.isChecked(), libraryChk.isChecked(), queueChk.isChecked()));
        }
    }

    // Gray out run button if no option is selected
    // TODO create a custom style to visually gray out the button when it's disabled
    private void refreshDisplay() {
        runBtn.setEnabled(queueChk.isChecked() || libraryChk.isChecked());
    }

    private Optional<JsonContentCollection> deserialiseJson(@NonNull DocumentFile jsonFile) {
        JsonContentCollection result;
        try {
            result = JsonHelper.jsonToObject(requireContext(), jsonFile, JsonContentCollection.class);
        } catch (IOException e) {
            Timber.w(e);
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private void runImport(@NonNull JsonContentCollection collection, boolean add, boolean importLibrary, boolean importQueue) {
        requireViewById(rootView, R.id.import_mode).setEnabled(false);
        libraryChk.setEnabled(false);
        queueChk.setEnabled(false);
        runBtn.setVisibility(View.GONE);
        setCancelable(false);

        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        if (!add) {
            if (importLibrary) dao.deleteAllInternalBooks(false);
            if (importQueue) dao.deleteAllQueuedBooks();
        }

        List<Content> all = new ArrayList<>();
        if (importLibrary) all.addAll(collection.getLibrary());
        if (importQueue) all.addAll(collection.getQueue());

        totalBooks = all.size();
        currentProgress = 0;
        nbSuccess = 0;
        progressBar.setMax(totalBooks);
        queueSize = (int) dao.countAllQueueBooks();

        importDisposable = Observable.fromIterable(all)
                .observeOn(Schedulers.io())
                .map(c -> importContent(c, dao))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::nextOK,
                        this::nextKO,
                        this::finish
                );
    }

    private boolean importContent(@NonNull Content c, CollectionDAO dao) {
        // Try to map the imported content to an existing book in the downloads folder
        // Folder names can be formatted in many ways _but_ they always contain the book unique ID !
        if (null == siteFoldersCache) siteFoldersCache = getSiteFolders();
        DocumentFile siteFolder = siteFoldersCache.get(c.getSite());
        if (siteFolder != null) mapToContent(c, siteFolder);
        Content duplicate = dao.selectContentBySourceAndUrl(c.getSite(), c.getUrl());
        if (null == duplicate) {
            long newContentId = dao.insertContent(c);
            // Insert queued content into the queue
            if (c.getStatus().equals(StatusContent.DOWNLOADING) || c.getStatus().equals(StatusContent.PAUSED)) {
                List<QueueRecord> lst = new ArrayList<>();
                lst.add(new QueueRecord(newContentId, queueSize++));
                dao.updateQueue(lst);
            }
        }

        return true;
    }

    private void mapToContent(@NonNull Content c, @NonNull DocumentFile siteFolder) {
        List<DocumentFile> bookfolders;
        if (bookFoldersCache.containsKey(c.getSite()))
            bookfolders = bookFoldersCache.get(c.getSite());
        else {
            bookfolders = FileHelper.listFolders(requireContext(), siteFolder);
            bookFoldersCache.put(c.getSite(), bookfolders);
        }
        boolean filesFound = false;
        if (bookfolders != null) {
            // Look for the book ID
            c.populateUniqueSiteId();
            for (DocumentFile f : bookfolders)
                if (f.getName() != null && f.getName().contains(ContentHelper.formatBookId(c))) {
                    // Cache folder Uri
                    c.setStorageUri(f.getUri().toString());
                    // Cache JSON Uri
                    DocumentFile json = FileHelper.findFile(requireContext(), f, Consts.JSON_FILE_NAME_V2);
                    if (json != null) c.setJsonUri(json.getUri().toString());
                    // Create the images from detected files
                    c.setImageFiles(ContentHelper.createImageListFromFolder(requireContext(), f));
                    filesFound = true;
                    break;
                }
        }
        // If no local storage found for the book, it goes in the errors queue (except if it already was in progress)
        if (!filesFound) {
            if (!c.getStatus().equals(StatusContent.DOWNLOADING) && !c.getStatus().equals(StatusContent.PAUSED))
                c.setStatus(StatusContent.ERROR);
            List<ErrorRecord> errors = new ArrayList<>();
            errors.add(new ErrorRecord(ErrorType.IMPORT, "", "Book", "No local images found when importing - Please redownload", Instant.now()));
            c.setErrorLog(errors);
        }
    }

    private Map<Site, DocumentFile> getSiteFolders() {
        Helper.assertNonUiThread();
        Map<Site, DocumentFile> result = new EnumMap<>(Site.class);

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(requireActivity(), Preferences.getStorageUri());
        if (null != rootFolder) {
            List<DocumentFile> subfolders = FileHelper.listFolders(requireContext(), rootFolder);
            String folderName;
            for (DocumentFile f : subfolders)
                if (f.getName() != null) {
                    folderName = f.getName().toLowerCase();
                    for (Site s : Site.values()) {
                        if (folderName.equalsIgnoreCase(s.getFolder())) {
                            result.put(s, f);
                            break;
                        }
                    }
                }
        }
        return result;
    }

    private void nextOK(boolean dummy) {
        nbSuccess++;
        updateProgress();
    }

    private void nextKO(Throwable e) {
        Timber.w(e);
        updateProgress();
    }

    private void updateProgress() {
        currentProgress++;
        progressTxt.setText(getResources().getString(R.string.book_progress, currentProgress, totalBooks));
        progressBar.setProgress(currentProgress);
        progressTxt.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void finish() {
        importDisposable.dispose();
        Snackbar.make(rootView, getResources().getQuantityString(R.plurals.import_result, nbSuccess, nbSuccess), LENGTH_LONG).show();

        // Dismiss after 3s, for the user to be able to see the snackbar
        new Handler().postDelayed(this::dismiss, 3000);
    }
}
