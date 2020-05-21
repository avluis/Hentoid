package me.devsaki.hentoid.fragments.preferences;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.Preferences;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 05/2020
 * Dialog for the library metadata export feature
 */
public class LibExportDialogFragment extends DialogFragment {
    private ViewGroup rootView;
    private View step1FolderButton;
    private ProgressBar step2progress;
    private View step2check;
    private View step3block;
    private TextView step3Txt;
    private ProgressBar step3progress;
    private View step3check;

    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        LibExportDialogFragment fragment = new LibExportDialogFragment();
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
        return inflater.inflate(R.layout.dialog_prefs_refresh, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        if (rootView instanceof ViewGroup) this.rootView = (ViewGroup) rootView;

        CheckBox renameChk = requireViewById(rootView, R.id.refresh_options_rename);
        CheckBox cleanAbsentChk = requireViewById(rootView, R.id.refresh_options_remove_1);
        CheckBox cleanNoImagesChk = requireViewById(rootView, R.id.refresh_options_remove_2);
        CheckBox cleanUnreadableChk = requireViewById(rootView, R.id.refresh_options_remove_3);

        View okBtn = requireViewById(rootView, R.id.refresh_ok);
        okBtn.setOnClickListener(v -> launchRefreshImport(renameChk.isChecked(), cleanAbsentChk.isChecked(), cleanNoImagesChk.isChecked(), cleanUnreadableChk.isChecked()));
    }

    private void launchRefreshImport(boolean rename, boolean cleanAbsent, boolean cleanNoImages, boolean cleanUnreadable) {
        showImportProgressLayout(false);
        setCancelable(false);

        // Run import
        ImportHelper.ImportOptions options = new ImportHelper.ImportOptions();
        options.rename = rename;
        options.cleanAbsent = cleanAbsent;
        options.cleanNoImages = cleanNoImages;
        options.cleanUnreadable = cleanUnreadable;

        Uri rootUri = Uri.parse(Preferences.getStorageUri());
        ImportHelper.setAndScanFolder(requireContext(), rootUri, false, null, options);
    }

    private void showImportProgressLayout(boolean askFolder) {
        // Replace launch options layout with import progress layout
        rootView.removeAllViews();
        LayoutInflater.from(getActivity()).inflate(R.layout.include_import_steps, rootView, true);

        // Memorize UI elements that will be updated during the import events
        step2progress = rootView.findViewById(R.id.import_step2_bar);
        step2check = rootView.findViewById(R.id.import_step2_check);
        step3block = rootView.findViewById(R.id.import_step3);
        step3progress = rootView.findViewById(R.id.import_step3_bar);
        step3Txt = rootView.findViewById(R.id.import_step3_text);
        step3check = rootView.findViewById(R.id.import_step3_check);

        step1FolderButton = rootView.findViewById(R.id.import_step1_button);
        if (askFolder) {
            step1FolderButton.setVisibility(View.VISIBLE);
            step1FolderButton.setOnClickListener(v -> selectHentoidFolder());
            selectHentoidFolder(); // Ask right away, there's no reason why the user should click again
        } else {
            ((TextView) rootView.findViewById(R.id.import_step1_folder)).setText(FileHelper.getFullPathFromTreeUri(requireContext(), Uri.parse(Preferences.getStorageUri()), true));
            rootView.findViewById(R.id.import_step1_check).setVisibility(View.VISIBLE);
            rootView.findViewById(R.id.import_step2).setVisibility(View.VISIBLE);
        }
    }

    private void selectHentoidFolder() {
        ImportHelper.openFolderPicker(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        @ImportHelper.Result int result = ImportHelper.processPickerResult(requireActivity(), requestCode, resultCode, data, this::onCancelExistingLibraryDialog, null);
        switch (result) {
            case ImportHelper.Result.OK_EMPTY_FOLDER:
                dismiss();
                break;
            case ImportHelper.Result.OK_LIBRARY_DETECTED:
                // Hentoid folder is finally selected at this point -> Update UI
                ((TextView) rootView.findViewById(R.id.import_step1_folder)).setText(FileHelper.getFullPathFromTreeUri(requireContext(), Uri.parse(Preferences.getStorageUri()), true));
                step1FolderButton.setVisibility(View.INVISIBLE);
                rootView.findViewById(R.id.import_step1_check).setVisibility(View.VISIBLE);
                rootView.findViewById(R.id.import_step2).setVisibility(View.VISIBLE);
                setCancelable(false);
                // Nothing else here; a dialog has been opened by ImportHelper
                break;
            case ImportHelper.Result.CANCELED:
                Snackbar.make(rootView, R.string.import_canceled, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case ImportHelper.Result.INVALID_FOLDER:
                Snackbar.make(rootView, R.string.import_invalid, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            case ImportHelper.Result.OTHER:
                Snackbar.make(rootView, R.string.import_other, BaseTransientBottomBar.LENGTH_LONG).show();
                break;
            default:
                // Nothing should happen here
        }
    }

    private void onCancelExistingLibraryDialog() {
        // Revert back to initial state where only the "Select folder" button is visible
        rootView.findViewById(R.id.import_step1_check).setVisibility(View.INVISIBLE);
        rootView.findViewById(R.id.import_step2).setVisibility(View.INVISIBLE);
        ((TextView) rootView.findViewById(R.id.import_step1_folder)).setText("");
        step1FolderButton.setVisibility(View.VISIBLE);
        setCancelable(true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImportEvent(ProcessEvent event) {
        ProgressBar progressBar = (2 == event.step) ? step2progress : step3progress;
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            progressBar.setMax(event.elementsTotal);
            progressBar.setProgress(event.elementsOK + event.elementsKO);
            if (3 == event.step)
                step3Txt.setText(getResources().getString(R.string.api29_migration_step3, event.elementsKO + event.elementsOK, event.elementsTotal));
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            if (2 == event.step) {
                step2check.setVisibility(View.VISIBLE);
                step3block.setVisibility(View.VISIBLE);
            } else if (3 == event.step) {
                step3Txt.setText(getResources().getString(R.string.api29_migration_step3, event.elementsTotal, event.elementsTotal));
                step3check.setVisibility(View.VISIBLE);
                dismiss();
            }
        }
    }
}
