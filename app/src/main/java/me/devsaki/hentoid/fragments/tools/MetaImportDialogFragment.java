package me.devsaki.hentoid.fragments.tools;

import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;
import static me.devsaki.hentoid.core.Consts.WORK_CLOSEABLE;

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
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.annimon.stream.Optional;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.databinding.DialogPrefsMetaImportBinding;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.events.ServiceDestroyedEvent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.workers.MetadataImportWorker;
import me.devsaki.hentoid.workers.data.MetadataImportData;
import timber.log.Timber;

/**
 * Dialog for the library metadata import feature
 */
public class MetaImportDialogFragment extends DialogFragment {

    // Empty files import options
    public static final int DONT_IMPORT = 0;
    public static final int IMPORT_AS_EMPTY = 1;
    public static final int IMPORT_AS_STREAMED = 2;
    public static final int IMPORT_AS_ERROR = 3;

    // UI
    private DialogPrefsMetaImportBinding binding = null;

    private boolean isServiceGracefulClose = false;

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
        EventBus.getDefault().register(this);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        String[] browseModes = getResources().getStringArray(R.array.tools_import_empty_books_entries);
        List<String> browseItems = new ArrayList<>(Arrays.asList(browseModes));

        binding.importEmptyBooksOptions.setItems(browseItems);
        binding.importEmptyBooksOptions.setOnSpinnerItemSelectedListener((i, o, i1, t1) -> refreshDisplay());

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
            int librarySize = collection.getJsonLibrary().size(); // Don't link the groups, just count the books
            if (librarySize > 0) {
                binding.importFileLibraryChk.setText(getResources().getQuantityString(R.plurals.import_file_library, librarySize, librarySize));
                binding.importFileLibraryChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
                binding.importFileLibraryChk.setVisibility(View.VISIBLE);
            }
            int mQueueSize = collection.getJsonQueue().size();
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
                    jsonFile.getUri().toString(),
                    binding.importModeAdd.isChecked(),
                    binding.importFileLibraryChk.isChecked(),
                    binding.importEmptyBooksOptions.getSelectedIndex(),
                    binding.importFileQueueChk.isChecked(),
                    binding.importFileGroupsChk.isChecked(),
                    binding.importFileBookmarksChk.isChecked())
            );
        }
    }

    // Gray out run button if no option is selected
    private void refreshDisplay() {
        binding.importEmptyBooksLabel.setVisibility(binding.importFileLibraryChk.isChecked() ? View.VISIBLE : View.GONE);
        binding.importEmptyBooksOptions.setVisibility(binding.importFileLibraryChk.isChecked() ? View.VISIBLE : View.GONE);

        binding.importRunBtn.setEnabled(
                (binding.importFileLibraryChk.isChecked() && binding.importEmptyBooksOptions.getSelectedIndex() > -1)
                        || binding.importFileQueueChk.isChecked()
                        || binding.importFileBookmarksChk.isChecked()
        );
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
            @NonNull final String jsonUri,
            boolean add,
            boolean importLibrary,
            int emptyBooksOption,
            boolean importQueue,
            boolean importCustomGroups,
            boolean importBookmarks) {
        binding.importMode.setEnabled(false);
        binding.importModeAdd.setEnabled(false);
        binding.importModeReplace.setEnabled(false);
        binding.importFileLibraryChk.setEnabled(false);
        binding.importFileQueueChk.setEnabled(false);
        binding.importFileGroupsChk.setEnabled(false);
        binding.importFileBookmarksChk.setEnabled(false);
        binding.importEmptyBooksOptions.setEnabled(false);

        binding.importRunBtn.setVisibility(View.GONE);
        setCancelable(false);

        MetadataImportData.Builder builder = new MetadataImportData.Builder();
        builder.setJsonUri(jsonUri);
        builder.setIsAdd(add);
        builder.setIsImportLibrary(importLibrary);
        builder.setEmptyBooksOption(emptyBooksOption);
        builder.setIsImportQueue(importQueue);
        builder.setIsImportCustomGroups(importCustomGroups);
        builder.setIsImportBookmarks(importBookmarks);

        ImportNotificationChannel.init(requireContext());

        WorkManager workManager = WorkManager.getInstance(requireContext());
        workManager.enqueueUniqueWork(Integer.toString(R.id.metadata_import_service),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                new OneTimeWorkRequest.Builder(MetadataImportWorker.class).setInputData(builder.getData()).addTag(WORK_CLOSEABLE).build());
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImportEvent(ProcessEvent event) {
        if (event.processId != R.id.import_metadata)
            return;

        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            int progress = event.elementsOK + event.elementsKO;
            String itemTxt = getResources().getQuantityString(R.plurals.item, progress);
            binding.importProgressText.setText(getResources().getString(R.string.generic_progress, progress, event.elementsTotal, itemTxt));
            binding.importProgressBar.setMax(event.elementsTotal);
            binding.importProgressBar.setProgress(progress);
            binding.importProgressText.setVisibility(View.VISIBLE);
            binding.importProgressBar.setVisibility(View.VISIBLE);
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            importDisposable.dispose();
            isServiceGracefulClose = true;
            Snackbar.make(binding.getRoot(), getResources().getQuantityString(R.plurals.import_result, event.elementsOK, event.elementsOK, event.elementsTotal), LENGTH_LONG).show();

            // Dismiss after 3s, for the user to be able to see the snackbar
            new Handler(Looper.getMainLooper()).postDelayed(this::dismissAllowingStateLoss, 3000);
        }
    }

    /**
     * Service destroyed event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServiceDestroyed(ServiceDestroyedEvent event) {
        if (event.service != R.id.metadata_import_service) return;
        if (!isServiceGracefulClose) {
            Snackbar.make(binding.getRoot(), R.string.import_unexpected, BaseTransientBottomBar.LENGTH_LONG).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::dismissAllowingStateLoss, 3000);
        }
    }
}
