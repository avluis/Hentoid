package me.devsaki.hentoid.fragments.preferences;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.io.IOException;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.json.JsonSettings;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG;

/**
 * Created by Robb on 03/2021
 * Dialog for the settings metadata import feature
 */
public class SettingsImportDialogFragment extends DialogFragment {

    // UI
    private ViewGroup rootView;
    private View selectFileBtn;
    private Handler dismissHandler;

    // Disposable for RxJava
    private Disposable importDisposable = Disposables.empty();

    private final ActivityResultLauncher<Integer> pickFile = registerForActivityResult(
            new ImportHelper.PickFileContract(),
            result -> onFilePickerResult(result.left, result.right)
    );


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
        selectFileBtn.setOnClickListener(v -> pickFile.launch(0));
    }

    @Override
    public void onDestroyView() {
        importDisposable.dispose();
        if (dismissHandler != null) dismissHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
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
        importDisposable = Single.fromCallable(() -> deserialiseJson(jsonFile))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        c -> onFileDeserialized(c, jsonFile),
                        t -> {
                            TextView errorTxt = requireViewById(rootView, R.id.import_file_invalid_text);
                            errorTxt.setText(getResources().getString(R.string.import_file_invalid, jsonFile.getName()));
                            errorTxt.setVisibility(View.VISIBLE);
                            Timber.w(t);
                        }
                );
    }

    private void onFileDeserialized(Optional<JsonSettings> collectionOptional, DocumentFile jsonFile) {
        importDisposable.dispose();

        TextView errorTxt = requireViewById(rootView, R.id.import_file_invalid_text);
        if (collectionOptional.isEmpty() || collectionOptional.get().getSettings().isEmpty()) {
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
