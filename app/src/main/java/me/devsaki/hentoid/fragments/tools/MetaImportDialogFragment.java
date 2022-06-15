package me.devsaki.hentoid.fragments.tools;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.databinding.DialogPrefsMetaImportBinding;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Dialog for the library metadata import feature
 */
public class MetaImportDialogFragment extends DialogFragment {

    // UI
    private DialogPrefsMetaImportBinding binding = null;

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
        binding = DialogPrefsMetaImportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        binding.importSelectFileBtn.setOnClickListener(v -> pickFile.launch(0));
    }

    private void onFilePickerResult(Integer resultCode, Uri uri) {
        switch (resultCode) {
            case ImportHelper.PickerResult.OK:
                // File selected
                DocumentFile doc = DocumentFile.fromSingleUri(requireContext(), uri);
                if (null == doc) return;
                binding.importSelectFileBtn.setVisibility(View.GONE);
                checkFile(doc);
                break;
            case ImportHelper.PickerResult.KO_CANCELED:
                Snackbar.make(binding.getRoot(), R.string.import_canceled, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case ImportHelper.PickerResult.KO_NO_URI:
                Snackbar.make(binding.getRoot(), R.string.import_invalid, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case ImportHelper.PickerResult.KO_OTHER:
                Snackbar.make(binding.getRoot(), R.string.import_other, BaseTransientBottomBar.LENGTH_LONG).show();
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

        if (collectionOptional.isEmpty() || collectionOptional.get().isEmpty()) {
            binding.importFileInvalidText.setText(getResources().getString(R.string.import_file_invalid, jsonFile.getName()));
            binding.importFileInvalidText.setVisibility(View.VISIBLE);
        } else {
            binding.importSelectFileBtn.setVisibility(View.GONE);
            binding.importFileInvalidText.setVisibility(View.GONE);

            JsonContentCollection collection = collectionOptional.get();
            int librarySize = collection.getLibrary(null).size(); // Don't link the groups, just count the books
            if (librarySize > 0) {
                binding.importFileLibraryChk.setText(getResources().getQuantityString(R.plurals.import_file_library, librarySize, librarySize));
                binding.importFileLibraryChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                binding.importFileLibraryChk.setVisibility(View.VISIBLE);
            }
            int mQueueSize = collection.getQueue().size();
            if (mQueueSize > 0) {
                binding.importFileQueueChk.setText(getResources().getQuantityString(R.plurals.import_file_queue, mQueueSize, mQueueSize));
                binding.importFileQueueChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                binding.importFileQueueChk.setVisibility(View.VISIBLE);
            }
            int mGroupsSize = collection.getCustomGroups().size();
            if (mGroupsSize > 0) {
                binding.importFileGroupsChk.setText(getResources().getQuantityString(R.plurals.import_file_groups, mGroupsSize, mGroupsSize));
                binding.importFileGroupsChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                binding.importFileGroupsChk.setVisibility(View.VISIBLE);
            }
            int bookmarksSize = collection.getBookmarks().size();
            if (bookmarksSize > 0) {
                binding.importFileBookmarksChk.setText(getResources().getQuantityString(R.plurals.import_file_bookmarks, bookmarksSize, bookmarksSize));
                binding.importFileBookmarksChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                binding.importFileBookmarksChk.setVisibility(View.VISIBLE);
            }
            binding.importRunBtn.setVisibility(View.VISIBLE);
            binding.importRunBtn.setEnabled(false);

            binding.importRunBtn.setOnClickListener(v -> runImport(
                    collection,
                    binding.importModeAdd.isChecked(),
                    binding.importFileLibraryChk.isChecked(),
                    binding.importFileQueueChk.isChecked(),
                    binding.importFileGroupsChk.isChecked(),
                    binding.importFileBookmarksChk.isChecked())
            );
        }
    }

    // Gray out run button if no option is selected
    private void refreshDisplay() {
        binding.importRunBtn.setEnabled(binding.importFileQueueChk.isChecked() || binding.importFileLibraryChk.isChecked() || binding.importFileBookmarksChk.isChecked());
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
        binding.importMode.setEnabled(false);
        binding.importFileLibraryChk.setEnabled(false);
        binding.importFileQueueChk.setEnabled(false);
        binding.importFileGroupsChk.setEnabled(false);
        binding.importFileBookmarksChk.setEnabled(false);
        binding.importRunBtn.setVisibility(View.GONE);
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
        binding.importProgressBar.setMax(totalItems);

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
        if (siteFolder != null) {
            boolean mappedToFiles = mapFilesToContent(c, siteFolder);
            // If no local storage found for the book, it goes in the errors queue (except if it already was in progress)
            if (!mappedToFiles) {
                if (!ContentHelper.isInQueue(c.getStatus())) c.setStatus(StatusContent.ERROR);
                List<ErrorRecord> errors = new ArrayList<>();
                errors.add(new ErrorRecord(ErrorType.IMPORT, "", "Book", "No local images found when importing - Please redownload", Instant.now()));
                c.setErrorLog(errors);
            }
        }
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

    private boolean mapFilesToContent(@NonNull final Content c, @NonNull final DocumentFile siteFolder) {
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
        return filesFound;
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
        binding.importProgressText.setText(getResources().getQuantityString(isGroup ? R.plurals.group_progress : R.plurals.book_progress, currentProgress, currentProgress, totalItems));
        binding.importProgressBar.setProgress(currentProgress);
        binding.importProgressText.setVisibility(View.VISIBLE);
        binding.importProgressBar.setVisibility(View.VISIBLE);
    }

    private void finish() {
        importDisposable.dispose();
        if (dao != null) dao.cleanup();
        if (nbSuccess > 0)
            Snackbar.make(binding.getRoot(), getResources().getQuantityString(R.plurals.import_result_books, nbSuccess, nbSuccess), LENGTH_LONG).show();
        else if (nbBookmarksSuccess > 0)
            Snackbar.make(binding.getRoot(), getResources().getQuantityString(R.plurals.import_result_bookmarks, nbBookmarksSuccess, nbBookmarksSuccess), LENGTH_LONG).show();

        // Dismiss after 3s, for the user to be able to see the snackbar
        new Handler(Looper.getMainLooper()).postDelayed(this::dismissAllowingStateLoss, 3000);
    }
}
