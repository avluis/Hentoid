package me.devsaki.hentoid.fragments.preferences;

import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.ThemeHelper;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

/**
 * Created by Robb on 05/2020
 * Dialog for the library metadata export feature
 */
public class LibExportDialogFragment extends DialogFragment {

    // UI
    private ViewGroup rootView;
    private CheckBox queueChk;
    private CheckBox libraryChk;
    private SwitchMaterial favsChk;
    private View runBtn;

    // Variable used during the import process
    private CollectionDAO dao;

    // Disposable for RxJava
    private Disposable exportDisposable = Disposables.empty();


    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        LibExportDialogFragment fragment = new LibExportDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_prefs_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        if (rootView instanceof ViewGroup) this.rootView = (ViewGroup) rootView;

        dao = new ObjectBoxDAO(requireContext());

        long nbLibraryBooks = dao.countAllInternalBooks(false);
        long nbQueueBooks = dao.countAllQueueBooks();

        libraryChk = requireViewById(rootView, R.id.export_file_library_chk);
        favsChk = requireViewById(rootView, R.id.export_favs_only);
        favsChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshFavsDisplay());
        if (nbLibraryBooks > 0) {
            libraryChk.setText(getResources().getQuantityString(R.plurals.export_file_library, (int) nbLibraryBooks, (int) nbLibraryBooks));
            libraryChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
            libraryChk.setVisibility(View.VISIBLE);
        }
        queueChk = requireViewById(rootView, R.id.export_file_queue_chk);
        if (nbQueueBooks > 0) {
            queueChk.setText(getResources().getQuantityString(R.plurals.export_file_queue, (int) nbQueueBooks, (int) nbQueueBooks));
            queueChk.setOnCheckedChangeListener((buttonView, isChecked) -> refreshDisplay());
            queueChk.setVisibility(View.VISIBLE);
        }

        runBtn = requireViewById(rootView, R.id.export_run_btn);
        runBtn.setEnabled(false);
        if (0 == nbLibraryBooks + nbLibraryBooks) runBtn.setVisibility(View.GONE);
        else
            runBtn.setOnClickListener(v -> runExport(libraryChk.isChecked(), favsChk.isChecked(), queueChk.isChecked()));
    }

    // Gray out run button if no option is selected
    // TODO create a custom style to visually gray out the run button when it's disabled
    private void refreshDisplay() {
        runBtn.setEnabled(queueChk.isChecked() || libraryChk.isChecked());
        favsChk.setVisibility(libraryChk.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void refreshFavsDisplay() {
        long nbLibraryBooks = dao.countAllInternalBooks(favsChk.isChecked());
        libraryChk.setText(getResources().getQuantityString(R.plurals.export_file_library, (int) nbLibraryBooks, (int) nbLibraryBooks));
    }

    private void runExport(boolean exportLibrary, boolean exportFavsOnly, boolean exportQueue) {
        queueChk.setEnabled(false);
        libraryChk.setEnabled(false);
        runBtn.setVisibility(View.GONE);
        setCancelable(false);

        ProgressBar progressBar = requireViewById(rootView, R.id.export_progress_bar);
        progressBar.setIndeterminate(true);
        // fixes <= Lollipop progressBar tinting
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            progressBar.getIndeterminateDrawable().setColorFilter(ThemeHelper.getColor(requireContext(), R.color.secondary_light), PorterDuff.Mode.SRC_IN);
        progressBar.setVisibility(View.VISIBLE);

        exportDisposable = Single.fromCallable(() -> getExportedCollection(exportLibrary, exportFavsOnly, exportQueue))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(c -> serializeJson(c.left, c.right))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        s -> onJsonSerialized(s, exportLibrary, exportFavsOnly, exportQueue),
                        Timber::w
                );
    }

    private ImmutablePair<List<Content>, List<Content>> getExportedCollection(boolean exportLibrary, boolean exportFavsOnly, boolean exportQueue) {
        List<Content> library = new ArrayList<>();
        if (exportLibrary) library.addAll(dao.selectAllInternalBooks(exportFavsOnly));
        List<Content> queue = new ArrayList<>();
        if (exportQueue) queue.addAll(dao.selectAllQueueBooks());
        return new ImmutablePair<>(library, queue);
    }

    private String serializeJson(@NonNull List<Content> library, @NonNull List<Content> queue) {
        JsonContentCollection jsonContentCollection = new JsonContentCollection();
        jsonContentCollection.setLibrary(library);
        jsonContentCollection.setQueue(queue);
        return JsonHelper.serializeToJson(jsonContentCollection, JsonContentCollection.class);
    }

    private void onJsonSerialized(@NonNull String json, boolean exportLibrary, boolean exportFavsOnly, boolean exportQueue) {
        exportDisposable.dispose();

        // Use a random number to avoid erasing older exports by mistake
        String targetFileName = new Random().nextInt(9999) + ".json";
        if (exportQueue) targetFileName = "queue-" + targetFileName;
        if (exportLibrary && !exportFavsOnly) targetFileName = "library-" + targetFileName;
        else if (exportLibrary) targetFileName = "favs-" + targetFileName;
        targetFileName = "export-" + targetFileName;

        try {
            try (OutputStream newDownload = FileHelper.openNewDownloadOutputStream(requireContext(), targetFileName, JsonHelper.JSON_MIME_TYPE)) {
                try (InputStream input = IOUtils.toInputStream(json, StandardCharsets.UTF_8)) {
                    FileHelper.copy(input, newDownload);
                }
            }

            Snackbar.make(rootView, R.string.viewer_copy_success, LENGTH_LONG)
                    .setAction("OPEN FOLDER", v -> FileHelper.openFile(requireContext(), FileHelper.getDownloadsFolder()))
                    .show();
        } catch (IOException | IllegalArgumentException e) {
            Snackbar.make(rootView, R.string.viewer_copy_fail, LENGTH_LONG).show();
        }

        // Dismiss after 3s, for the user to be able to see and use the snackbar
        new Handler().postDelayed(this::dismiss, 3000);
    }
}
