package me.devsaki.hentoid.fragments.preferences;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
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
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

/**
 * Created by Robb on 05/2020
 * Dialog for the library metadata import feature
 */
public class MetaImportDialogFragment extends DialogFragment {

    // UI
    private ViewGroup rootView;
    private View selectFileBtn;
    private TextView progressTxt;
    private ProgressBar progressBar;
    private CheckBox libraryChk;
    private CheckBox queueChk;
    private CheckBox groupsChk;
    private CheckBox bookmarksChk;
    private View runBtn;

    // Variable used during the import process
    private CollectionDAO dao;
    private int totalItems;
    private int currentProgress;
    private int nbSuccess;
    private int queueSize;
    private int nbBookmarksSuccess = 0;
    private Map<Site, DocumentFile> siteFoldersCache = null;
    private final Map<Site, List<DocumentFile>> bookFoldersCache = new EnumMap<>(Site.class);

    // Disposable for RxJava
    private Disposable importDisposable = Disposables.empty();

    private final ActivityResultLauncher<Integer> pickFile = registerForActivityResult(
            new ImportHelper.PickFileContract(),
            result -> onFilePickerResult(result.left, result.right)
    );


    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        MetaImportDialogFragment fragment = new MetaImportDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_prefs_meta_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (rootView instanceof ViewGroup) this.rootView = (ViewGroup) rootView;

        progressTxt = requireViewById(rootView, R.id.import_progress_text);
        progressBar = requireViewById(rootView, R.id.import_progress_bar);

        selectFileBtn = requireViewById(rootView, R.id.import_select_file_btn);
        selectFileBtn.setOnClickListener(v -> pickFile.launch(0));
    }

    private void onFilePickerResult(Integer resultCode, Uri uri) {
        switch (resultCode) {
            case ImportHelper.PickerResult.OK:
                // File selected
                DocumentFile doc = DocumentFile.fromSingleUri(requireContext(), uri);
                if (null == doc) return;
                selectFileBtn.setVisibility(View.GONE);
                checkFile(doc);
                break;
            case ImportHelper.PickerResult.KO_CANCELED:
                Snackbar.make(rootView, R.string.import_canceled, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case ImportHelper.PickerResult.KO_NO_URI:
                Snackbar.make(rootView, R.string.import_invalid, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case ImportHelper.PickerResult.KO_OTHER:
                Snackbar.make(rootView, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            default:
                // Nothing should happen here
        }
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
        if (collectionOptional.isEmpty() || collectionOptional.get().isEmpty()) {
            errorTxt.setText(getResources().getString(R.string.import_file_invalid, jsonFile.getName()));
            errorTxt.setVisibility(View.VISIBLE);
        } else {
            selectFileBtn.setVisibility(View.GONE);
            errorTxt.setVisibility(View.GONE);

            JsonContentCollection collection = collectionOptional.get();
            libraryChk = requireViewById(rootView, R.id.import_file_library_chk);
            int librarySize = collection.getLibrary(null).size(); // Don't link the groups, just count the books
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
            groupsChk = requireViewById(rootView, R.id.import_file_groups_chk);
            int mGroupsSize = collection.getCustomGroups().size();
            if (mGroupsSize > 0) {
                groupsChk.setText(getResources().getQuantityString(R.plurals.import_file_groups, mGroupsSize, mGroupsSize));
                groupsChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                groupsChk.setVisibility(View.VISIBLE);
            }
            bookmarksChk = requireViewById(rootView, R.id.import_file_bookmarks_chk);
            int bookmarksSize = collection.getBookmarks().size();
            if (bookmarksSize > 0) {
                bookmarksChk.setText(getResources().getQuantityString(R.plurals.import_file_bookmarks, bookmarksSize, bookmarksSize));
                bookmarksChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                bookmarksChk.setVisibility(View.VISIBLE);
            }
            requireViewById(rootView, R.id.import_warning_img).setVisibility(View.VISIBLE);
            requireViewById(rootView, R.id.import_file_help_text).setVisibility(View.VISIBLE);
            runBtn = requireViewById(rootView, R.id.import_run_btn);
            runBtn.setVisibility(View.VISIBLE);
            runBtn.setEnabled(false);

            RadioButton addChk = requireViewById(rootView, R.id.import_mode_add);
            runBtn.setOnClickListener(v -> runImport(collection, addChk.isChecked(), libraryChk.isChecked(), queueChk.isChecked(), groupsChk.isChecked(), bookmarksChk.isChecked()));
        }
    }

    // Gray out run button if no option is selected
    // TODO create a custom style to visually gray out the button when it's disabled
    private void refreshDisplay() {
        runBtn.setEnabled(queueChk.isChecked() || libraryChk.isChecked() || bookmarksChk.isChecked());
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

    private void runImport(
            @NonNull final JsonContentCollection collection,
            boolean add,
            boolean importLibrary,
            boolean importQueue,
            boolean importCustomGroups,
            boolean importBookmarks) {
        requireViewById(rootView, R.id.import_mode).setEnabled(false);
        libraryChk.setEnabled(false);
        queueChk.setEnabled(false);
        groupsChk.setEnabled(false);
        bookmarksChk.setEnabled(false);
        runBtn.setVisibility(View.GONE);
        setCancelable(false);

        dao = new ObjectBoxDAO(requireContext());
        if (!add) {
            if (importLibrary) dao.deleteAllInternalBooks(false);
            if (importQueue) dao.deleteAllQueuedBooks();
            if (importCustomGroups) dao.deleteAllGroups(Grouping.CUSTOM);
            if (importBookmarks) dao.deleteAllBookmarks();
        }

        if (importBookmarks)
            nbBookmarksSuccess = ImportHelper.importBookmarks(dao, collection.getBookmarks());

        List<Content> contentToImport = new ArrayList<>();
        if (importLibrary) contentToImport.addAll(collection.getLibrary(dao));
        if (importQueue) contentToImport.addAll(collection.getQueue());
        queueSize = (int) dao.countAllQueueBooks();

        if (importCustomGroups)
            // Chain group import followed by content import
            runImportItems(
                    collection.getCustomGroups(),
                    dao,
                    true,
                    () -> runImportItems(contentToImport, dao, false, this::finish)
            );
        else // Run content import alone
            runImportItems(contentToImport, dao, false, this::finish);
    }

    private void runImportItems(@NonNull final List<?> items,
                                @NonNull final CollectionDAO dao,
                                boolean isGroup,
                                @NonNull final Runnable onFinish) {
        totalItems = items.size();
        currentProgress = 0;
        nbSuccess = 0;
        progressBar.setMax(totalItems);

        importDisposable = Observable.fromIterable(items)
                .observeOn(Schedulers.io())
                .map(c -> importItem(c, dao))
                .doOnComplete(() -> {
                    if (isGroup) GroupHelper.updateGroupsJson(requireContext(), dao);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        b -> nextOK(isGroup),
                        e -> nextKO(e, isGroup),
                        onFinish::run
                );
    }

    private boolean importItem(@NonNull final Object o, @NonNull final CollectionDAO dao) {
        if (o instanceof Content) importContent((Content) o, dao);
        else if (o instanceof Group) importGroup((Group) o, dao);
        return true;
    }

    private void importContent(@NonNull final Content c, @NonNull final CollectionDAO dao) {
        // Try to map the imported content to an existing book in the downloads folder
        // Folder names can be formatted in many ways _but_ they always contain the book unique ID !
        if (null == siteFoldersCache) siteFoldersCache = getSiteFolders();
        DocumentFile siteFolder = siteFoldersCache.get(c.getSite());
        if (siteFolder != null) mapToContent(c, siteFolder);
        Content duplicate = dao.selectContentBySourceAndUrl(c.getSite(), c.getUrl(), "");
        if (null == duplicate) {
            long newContentId = ContentHelper.addContent(requireContext(), dao, c);
            // Insert queued content into the queue
            if (c.getStatus().equals(StatusContent.DOWNLOADING) || c.getStatus().equals(StatusContent.PAUSED)) {
                List<QueueRecord> lst = new ArrayList<>();
                lst.add(new QueueRecord(newContentId, queueSize++));
                dao.updateQueue(lst);
            }
        }
    }

    private void mapToContent(@NonNull final Content c, @NonNull final DocumentFile siteFolder) {
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

    private void importGroup(@NonNull final Group group, @NonNull final CollectionDAO dao) {
        if (null == dao.selectGroupByName(Grouping.CUSTOM.getId(), group.name))
            dao.insertGroup(group);
    }

    private void nextOK(boolean isGroup) {
        nbSuccess++;
        updateProgress(isGroup);
    }

    private void nextKO(Throwable e, boolean isGroup) {
        Timber.w(e);
        updateProgress(isGroup);
    }

    private void updateProgress(boolean isGroup) {
        currentProgress++;
        progressTxt.setText(getResources().getString(isGroup ? R.string.group_progress : R.string.book_progress, currentProgress, totalItems));
        progressBar.setProgress(currentProgress);
        progressTxt.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void finish() {
        importDisposable.dispose();
        if (dao != null) dao.cleanup();
        if (nbSuccess > 0)
            Snackbar.make(rootView, getResources().getQuantityString(R.plurals.import_result_books, nbSuccess, nbSuccess), LENGTH_LONG).show();
        else if (nbBookmarksSuccess > 0)
            Snackbar.make(rootView, getResources().getQuantityString(R.plurals.import_result_bookmarks, nbBookmarksSuccess, nbBookmarksSuccess), LENGTH_LONG).show();

        // Dismiss after 3s, for the user to be able to see the snackbar
        new Handler(Looper.getMainLooper()).postDelayed(this::dismiss, 3000);
    }
}
