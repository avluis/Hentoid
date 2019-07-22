package me.devsaki.hentoid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImportActivity;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class LibRefreshDialogFragment extends DialogFragment {

    public static void invoke(FragmentManager fragmentManager) {
        LibRefreshDialogFragment fragment = new LibRefreshDialogFragment();
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_refresh, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CheckBox renameChk = requireViewById(view, R.id.refresh_options_rename);
        CheckBox cleanAbsentChk = requireViewById(view, R.id.refresh_options_remove_1);
        CheckBox cleanNoImagesChk = requireViewById(view, R.id.refresh_options_remove_2);
        CheckBox cleanUnreadableChk = requireViewById(view, R.id.refresh_options_remove_3);

        View okBtn = requireViewById(view, R.id.refresh_ok);
        okBtn.setOnClickListener(v -> launchRefreshImport(renameChk.isChecked(), cleanAbsentChk.isChecked(), cleanNoImagesChk.isChecked(), cleanUnreadableChk.isChecked()));
    }

    private void launchRefreshImport(boolean rename, boolean cleanAbsent, boolean cleanNoImages, boolean cleanUnreadable) {
        Intent refresh = new Intent(requireContext(), ImportActivity.class);
        refresh.setAction("android.intent.action.APPLICATION_PREFERENCES"); // Is only a constant since API 24 -> using the string

        ImportActivityBundle.Builder builder = new ImportActivityBundle.Builder();

        builder.setRefresh(true);
        builder.setRefreshRename(rename);
        builder.setRefreshCleanAbsent(cleanAbsent);
        builder.setRefreshCleanNoImages(cleanNoImages);
        builder.setRefreshCleanUnreadable(cleanUnreadable);

        refresh.putExtras(builder.getBundle());

        startActivity(refresh);
        dismiss();
    }
}
