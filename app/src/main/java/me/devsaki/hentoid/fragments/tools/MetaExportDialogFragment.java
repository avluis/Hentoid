package me.devsaki.hentoid.fragments.tools;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.ContextXKt;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import timber.log.Timber;

/**
 * Dialog for the settings metadata export feature
 */
public class MetaExportDialogFragment extends DialogFragment {

    // UI
    private ViewGroup rootView;
    private CheckBox libraryChk;
    private SwitchMaterial favsChk;
    private SwitchMaterial groupsChk;
    private CheckBox queueChk;
    private CheckBox bookmarksChk;
    private View runBtn;
    private ProgressBar progressBar;

    // Variable used during the import process
    private CollectionDAO dao;

    // Disposable for RxJava
    private Disposable exportDisposable = Disposables.empty();


    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        MetaExportDialogFragment fragment = new MetaExportDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_prefs_meta_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        if (rootView instanceof ViewGroup) this.rootView = (ViewGroup) rootView;

        dao = new ObjectBoxDAO(requireContext());

        long nbLibraryBooks = dao.countAllInternalBooks(false);
        long nbQueueBooks = dao.countAllQueueBooks();
        long nbBookmarks = dao.countAllBookmarks();

        libraryChk = requireViewById(rootView, R.id.export_file_library_chk);
        favsChk = requireViewById(rootView, R.id.export_favs_only);
        favsChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshFavsDisplay());
        if (nbLibraryBooks > 0) {
            libraryChk.setText(getResources().getQuantityString(R.plurals.export_file_library, (int) nbLibraryBooks, (int) nbLibraryBooks));
            libraryChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
            libraryChk.setVisibility(View.VISIBLE);
        }
        groupsChk = requireViewById(rootView, R.id.export_groups);
        queueChk = requireViewById(rootView, R.id.export_file_queue_chk);
        if (nbQueueBooks > 0) {
            queueChk.setText(getResources().getQuantityString(R.plurals.export_file_queue, (int) nbQueueBooks, (int) nbQueueBooks));
            queueChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
            queueChk.setVisibility(View.VISIBLE);
        }
        bookmarksChk = requireViewById(rootView, R.id.export_file_bookmarks_chk);
        if (nbBookmarks > 0) {
            bookmarksChk.setText(getResources().getQuantityString(R.plurals.export_file_bookmarks, (int) nbBookmarks, (int) nbBookmarks));
            bookmarksChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
            bookmarksChk.setVisibility(View.VISIBLE);
        }

        // Open library transfer FAQ
        requireViewById(rootView, R.id.export_file_help1_text)
                .setOnClickListener(v -> ContextXKt.startBrowserActivity(requireActivity(), getResources().getString(R.string.export_faq_url)));
        requireViewById(rootView, R.id.info_img)
                .setOnClickListener(v -> ContextXKt.startBrowserActivity(requireActivity(), getResources().getString(R.string.export_faq_url)));

        runBtn = requireViewById(rootView, R.id.export_run_btn);
        runBtn.setEnabled(false);
        if (0 == nbLibraryBooks + nbQueueBooks + nbBookmarks) runBtn.setVisibility(View.GONE);
        else
            runBtn.setOnClickListener(v -> runExport(libraryChk.isChecked(), favsChk.isChecked(), groupsChk.isChecked(), queueChk.isChecked(), bookmarksChk.isChecked()));

        progressBar = requireViewById(rootView, R.id.export_progress_bar);
    }

    // Gray out run button if no option is selected
    private void refreshDisplay() {
        runBtn.setEnabled(queueChk.isChecked() || libraryChk.isChecked() || bookmarksChk.isChecked());
        favsChk.setVisibility(libraryChk.isChecked() ? View.VISIBLE : View.GONE);
        groupsChk.setVisibility(libraryChk.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void refreshFavsDisplay() {
        long nbLibraryBooks = dao.countAllInternalBooks(favsChk.isChecked());
        libraryChk.setText(getResources().getQuantityString(R.plurals.export_file_library, (int) nbLibraryBooks, (int) nbLibraryBooks));
    }

    private void runExport(
            boolean exportLibrary,
            boolean exportFavsOnly,
            boolean exportCustomGroups,
            boolean exportQueue,
            boolean exportBookmarks) {
        libraryChk.setEnabled(false);
        queueChk.setEnabled(false);
        bookmarksChk.setEnabled(false);
        runBtn.setVisibility(View.GONE);
        setCancelable(false);

        progressBar.setIndeterminate(true);
        // fixes <= Lollipop progressBar tinting
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            progressBar.getIndeterminateDrawable().setColorFilter(ThemeHelper.getColor(requireContext(), R.color.secondary_light), PorterDuff.Mode.SRC_IN);
        progressBar.setVisibility(View.VISIBLE);

        exportDisposable = Single.fromCallable(() -> getExportedCollection(exportLibrary, exportFavsOnly, exportCustomGroups, exportQueue, exportBookmarks))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(c -> {
                    progressBar.setMax(3);
                    progressBar.setProgress(1);
                    progressBar.setIndeterminate(false);
                    return JsonHelper.serializeToJson(c, JsonContentCollection.class);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        s -> {
                            progressBar.setProgress(2);
                            onJsonSerialized(s, exportLibrary, exportFavsOnly, exportQueue, exportBookmarks);
                            progressBar.setProgress(3);
                        },
                        t -> {
                            Timber.w(t);
                            Helper.logException(t);
                            Snackbar.make(rootView, R.string.export_failed, LENGTH_LONG).show();
                            // Dismiss after 3s, for the user to be able to see and use the snackbar
                            new Handler(Looper.getMainLooper()).postDelayed(this::dismissAllowingStateLoss, 3000);
                        }
                );
    }

    private JsonContentCollection getExportedCollection(
            boolean exportLibrary,
            boolean exportFavsOnly,
            boolean exportCustomgroups,
            boolean exportQueue,
            boolean exportBookmarks) {
        JsonContentCollection jsonContentCollection = new JsonContentCollection();

        if (exportLibrary)
            dao.streamAllInternalBooks(exportFavsOnly, jsonContentCollection::addToLibrary); // Using streaming here to support large collections

        if (exportQueue) jsonContentCollection.setQueue(dao.selectAllQueueBooks());
        if (exportCustomgroups)
            jsonContentCollection.setCustomGroups(dao.selectGroups(Grouping.CUSTOM.getId()));
        if (exportBookmarks) jsonContentCollection.setBookmarks(dao.selectAllBookmarks());

        return jsonContentCollection;
    }

    private void onJsonSerialized(
            @NonNull String json,
            boolean exportLibrary,
            boolean exportFavsOnly,
            boolean exportQueue,
            boolean exportBookmarks) {
        exportDisposable.dispose();

        // Use a random number to avoid erasing older exports by mistake
        String targetFileName = Helper.getRandomInt(9999) + ".json";
        if (exportBookmarks) targetFileName = "bkmks-" + targetFileName;
        if (exportQueue) targetFileName = "queue-" + targetFileName;
        if (exportLibrary && !exportFavsOnly) targetFileName = "library-" + targetFileName;
        else if (exportLibrary) targetFileName = "favs-" + targetFileName;
        targetFileName = "export-" + targetFileName;

        try {
            try (OutputStream newDownload = FileHelper.openNewDownloadOutputStream(requireContext(), targetFileName, JsonHelper.JSON_MIME_TYPE)) {
                try (InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                    Helper.copy(input, newDownload);
                }
            }

            Snackbar.make(rootView, R.string.copy_download_folder_success, LENGTH_LONG)
                    .setAction(R.string.open_folder, v -> FileHelper.openFile(requireContext(), FileHelper.getDownloadsFolder()))
                    .show();
        } catch (IOException | IllegalArgumentException e) {
            Snackbar.make(rootView, R.string.copy_download_folder_fail, LENGTH_LONG).show();
        }

        if (dao != null) dao.cleanup();
        // Dismiss after 3s, for the user to be able to see and use the snackbar
        new Handler(Looper.getMainLooper()).postDelayed(this::dismissAllowingStateLoss, 3000);
    }
}
