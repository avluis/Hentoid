package me.devsaki.hentoid.fragments.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
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
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

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

    // Variable used during the selection process
    private Uri selectedFileUri;

    // Variable used during the import process
    private CollectionDAO dao;
    private int totalBooks;
    private int currentProgress;
    private Map<Site, DocumentFile> siteFoldersCache = null;
    private Map<Site, List<DocumentFile>> bookFoldersCache = new HashMap<>();

    // Disposable for RxJava
    private Disposable importDisposable = Disposables.empty();

    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        LibImportDialogFragment fragment = new LibImportDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        super.onDestroyView();
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
                setCancelable(false);
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
            CheckBox libraryChk = requireViewById(rootView, R.id.import_file_library_chk);
            int librarySize = collection.getLibrary().size();
            if (librarySize > 0) {
                libraryChk.setText(getResources().getQuantityString(R.plurals.import_file_library, librarySize, librarySize));
                libraryChk.setVisibility(View.VISIBLE);
            }
            CheckBox queueChk = requireViewById(rootView, R.id.import_file_queue_chk);
            int queueSize = collection.getQueue().size();
            if (queueSize > 0) {
                queueChk.setText(getResources().getQuantityString(R.plurals.import_file_queue, queueSize, queueSize));
                queueChk.setVisibility(View.VISIBLE);
            }
            requireViewById(rootView, R.id.import_warning_img).setVisibility(View.VISIBLE);
            requireViewById(rootView, R.id.import_file_help_text).setVisibility(View.VISIBLE);
            View runImportBtn = requireViewById(rootView, R.id.import_mode_add);
            runImportBtn.setVisibility(View.VISIBLE);
            RadioButton addChk = requireViewById(rootView, R.id.import_mode_add);
            runImportBtn.setOnClickListener(v -> runImport(collection, addChk.isChecked(), libraryChk.isChecked(), queueChk.isChecked()));
        }
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
        requireViewById(rootView, R.id.import_file_library_chk).setEnabled(false);
        requireViewById(rootView, R.id.import_file_queue_chk).setEnabled(false);
        requireViewById(rootView, R.id.import_run_btn).setVisibility(View.GONE);

        dao = new ObjectBoxDAO(requireContext());
        if (!add) {
            if (importLibrary) dao.deleteAllLibraryBooks();
            if (importQueue) dao.deleteAllQueuedBooks();
        }

        List<Content> all = new ArrayList<>();
        if (importLibrary) all.addAll(collection.getLibrary());
        if (importQueue) all.addAll(collection.getQueue());

        totalBooks = all.size();
        currentProgress = 0;
        progressBar.setMax(totalBooks);

        importDisposable = Observable.fromIterable(all)
                .observeOn(Schedulers.io())
                .map(c -> importContent(c, dao))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::next,
                        Timber::w,
                        this::finish
                );
    }

    private boolean importContent(@NonNull Content c, CollectionDAO dao) {
        // Try to map the imported content to an existing book in the downloads folder
        // Folder names can be formatted in many ways _but_ they always contain the book unique ID !
        if (null == siteFoldersCache) siteFoldersCache = getSiteFolders();
        DocumentFile siteFolder = siteFoldersCache.get(c.getSite());
        if (siteFolder != null) mapToContent(c, siteFolder);
        dao.insertContent(c);

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
        if (bookfolders != null) {
            // Look for the book ID
            c.populateUniqueSiteId();
            for (DocumentFile f : bookfolders)
                if (f.getName() != null && f.getName().contains("[" + c.getUniqueSiteId() + "]")) {
                    // Cache folder Uri
                    c.setStorageUri(f.getUri().toString());
                    // Cache JSON Uri
                    DocumentFile json = FileHelper.findFile(requireContext(), f, Consts.JSON_FILE_NAME_V2);
                    if (json != null) c.setJsonUri(json.getUri().toString());
                    // Create the images from detected files
                    c.setImageFiles(ContentHelper.createImageListFromFolder(requireContext(), f));
                    break;
                }
        }
    }

    private Map<Site, DocumentFile> getSiteFolders() {
        Helper.assertNonUiThread();
        Map<Site, DocumentFile> result = new HashMap<>();

        if (!Preferences.getStorageUri().isEmpty()) {
            Uri rootUri = Uri.parse(Preferences.getStorageUri());
            DocumentFile rootFolder = DocumentFile.fromTreeUri(requireContext(), rootUri);
            if (rootFolder != null && rootFolder.exists()) {
                List<DocumentFile> subfolders = FileHelper.listFolders(requireContext(), rootFolder);
                String folderName;
                for (DocumentFile f : subfolders)
                    if (f.getName() != null) {
                        folderName = f.getName().toLowerCase();
                        for (Site s : Site.values()) {
                            if (folderName.equals(s.getFolder().toLowerCase())) {
                                result.put(s, f);
                                break;
                            }
                        }
                    }
            }
        }
        return result;
    }

    private void next(boolean success) {
        currentProgress++;
        progressTxt.setText(getResources().getString(R.string.book_progress, currentProgress, totalBooks));
        progressBar.setProgress(currentProgress);
        progressTxt.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void finish() {
        importDisposable.dispose();
        dismiss();
    }
}
