package me.devsaki.hentoid.fragments.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.json.JsonSettings;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

/**
 * Created by Robb on 05/2020
 * Dialog for the library metadata import feature
 */
public class SettingsImportDialogFragment extends DialogFragment {

    private static final int RQST_PICK_IMPORT_FILE = 4;

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
    private Handler dismissHandler;

    // Variable used during the selection process
    private Uri selectedFileUri;

    // Disposable for RxJava
    private Disposable importDisposable = Disposables.empty();


    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        SettingsImportDialogFragment fragment = new SettingsImportDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_prefs_settings_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (rootView instanceof ViewGroup) this.rootView = (ViewGroup) rootView;

        selectFileBtn = requireViewById(rootView, R.id.import_select_file_btn);
        selectFileBtn.setOnClickListener(v -> askFile());
    }

    @Override
    public void onDestroyView() {
        importDisposable.dispose();
        if (dismissHandler != null) dismissHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void askFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
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
        importDisposable = Single.fromCallable(() -> deserialiseJson(jsonFile))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        c -> onFileDeserialized(c, jsonFile),
                        Timber::w
                );
    }

    private void onFileDeserialized(Optional<JsonSettings> collectionOptional, DocumentFile jsonFile) {
        importDisposable.dispose();

        TextView errorTxt = requireViewById(rootView, R.id.import_file_invalid_text);
        if (collectionOptional.isEmpty()) {
            errorTxt.setText(getResources().getString(R.string.import_file_invalid, jsonFile.getName()));
            errorTxt.setVisibility(View.VISIBLE);
        } else {
            selectFileBtn.setVisibility(View.GONE);
            errorTxt.setVisibility(View.GONE);

            JsonSettings collection = collectionOptional.get();

            runImport(collection);
        }
    }

    private Optional<JsonSettings> deserialiseJson(@NonNull DocumentFile jsonFile) {
        JsonSettings result;
        try {
            result = JsonHelper.jsonToObject(requireContext(), jsonFile, JsonSettings.class);
        } catch (IOException e) {
            Timber.w(e);
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private void runImport(@NonNull final JsonSettings settings) {
        setCancelable(false);

        Preferences.importInformation(settings.getSettings());

        finish();
    }

    private void finish() {
        importDisposable.dispose();

        Snackbar.make(rootView, getResources().getString(R.string.import_settings_success), LENGTH_LONG).show();

        // Dismiss after 3s, for the user to be able to see the snackbar
        dismissHandler = new Handler(Looper.getMainLooper());
        dismissHandler.postDelayed(this::dismiss, 3000);
    }
}
