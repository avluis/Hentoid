package me.devsaki.hentoid.fragments.import_;

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
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class LibRefreshDialogFragment extends DialogFragment {

    private ViewGroup rootView;
    private ProgressBar step2progress;
    private View step2check;
    private View step3block;
    private TextView step3Txt;
    private ProgressBar step3progress;
    private View step3check;

    public static void invoke(FragmentManager fragmentManager) {
        LibRefreshDialogFragment fragment = new LibRefreshDialogFragment();
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
        // Replace launch options layout with progress layout
        rootView.removeAllViews();
        LayoutInflater.from(getActivity()).inflate(R.layout.include_import_steps, rootView, true);

        // Hentoid folder is known -> Update UI accordingly
        ((TextView)rootView.findViewById(R.id.import_step1_folder)).setText(FileHelper.getFullPathFromTreeUri(requireContext(), Uri.parse(Preferences.getStorageUri()), true));
        rootView.findViewById(R.id.import_step1_button).setVisibility(View.GONE);
        rootView.findViewById(R.id.import_step1_check).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.import_step2).setVisibility(View.VISIBLE);

        // Memorize UI elements that will be updated during the import events
        step2progress = rootView.findViewById(R.id.import_step2_bar);
        step2check = rootView.findViewById(R.id.import_step2_check);
        step3block = rootView.findViewById(R.id.import_step3);
        step3progress = rootView.findViewById(R.id.import_step3_bar);
        step3Txt = rootView.findViewById(R.id.import_step3_text);
        step3check = rootView.findViewById(R.id.import_step3_check);

        // Run import
        // TODO display "refresh" on the notifications
        ImportHelper.ImportOptions options = new ImportHelper.ImportOptions();
        options.rename = rename;
        options.cleanAbsent = cleanAbsent;
        options.cleanNoImages = cleanNoImages;
        options.cleanUnreadable = cleanUnreadable;

        Uri rootUri = Uri.parse(Preferences.getStorageUri());
        ImportHelper.setAndScanFolder(requireContext(), rootUri, true, options);
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
