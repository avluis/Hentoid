package me.devsaki.hentoid.fragments.import_;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 08/2019
 * Hentoid folder dialog for kitkat (replaces the SAF directory picker that only exists for Lollipop+)
 */
public class KitkatDownloadFragment extends DialogFragment {

    private EditText subfolderEdit;
    private RadioGroup radioGroup;
    private final List<String> extFoldersList = new ArrayList<>();

    private View publicTxt;
    private View privateImg;
    private View privateTxt;

    private int displayMode = -1;

    public static void invoke(FragmentManager fragmentManager) {
        KitkatDownloadFragment fragment = new KitkatDownloadFragment();
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_kitkat_download_folder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String currentFolder = Preferences.getRootFolderName();
        String currentRoot = "";
        String root;

        radioGroup = requireViewById(view, R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener(this::onChangeRoot);

        RadioButton defaultRootBtn = requireViewById(view, R.id.kitkat_btn_default_root);
        String label = defaultRootBtn.getText().toString();
        root = Environment.getExternalStorageDirectory().getAbsolutePath();
        label = label.replace("@defaultDir", root);
        defaultRootBtn.setText(label);
        if (currentFolder.startsWith(root)) {
            currentRoot = root;
            defaultRootBtn.setChecked(true);
        }

        int i = 1;
        // Adds detected private external roots
        for (File f : requireContext().getExternalFilesDirs(null)) {
            root = f.getAbsolutePath();
            // Only add if the root is different than Hentoid's default
            // (no point in suggesting a private root in the same volume as the default)
            if (!root.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                extFoldersList.add(root);
                RadioButton btn = new RadioButton(requireContext());
                btn.setText(String.format(Locale.US, "External private folder #%d(%s)", i++, root));
                if (currentFolder.startsWith(root)) {
                    currentRoot = root;
                    btn.setChecked(true);
                }
                radioGroup.addView(btn);
            }
        }

        publicTxt = requireViewById(view, R.id.kitkat_dl_folder_public_txt);
        privateImg = requireViewById(view, R.id.kitkat_dl_folder_private_img);
        privateTxt = requireViewById(view, R.id.kitkat_dl_folder_private_txt);

        subfolderEdit = requireViewById(view, R.id.kitkat_dl_folder_subfolder);
        if (!currentRoot.isEmpty()) subfolderEdit.setText(currentFolder.replace(currentRoot,""));

        View okBtn = requireViewById(view, R.id.kitkat_dl_folder_ok);
        okBtn.setOnClickListener(v -> onOkClick(view));

        updateDisplayText();
    }

    private void onChangeRoot(RadioGroup group, int checkedId) {
        updateDisplayText();
    }

    private void updateDisplayText() {
        if (radioGroup.getCheckedRadioButtonId() == R.id.kitkat_btn_default_root) {
            privateImg.setVisibility(View.INVISIBLE);
            privateTxt.setVisibility(View.INVISIBLE);
            publicTxt.setVisibility(View.VISIBLE);
        } else {
            publicTxt.setVisibility(View.GONE);
            privateImg.setVisibility(View.VISIBLE);
            privateTxt.setVisibility(View.VISIBLE);
        }
    }

    private void onOkClick(View view) {
        File targetFolder = null;
        if (radioGroup.getCheckedRadioButtonId() == R.id.kitkat_btn_default_root)
            targetFolder = Environment.getExternalStorageDirectory();
        else {
            for (int i=0; i < extFoldersList.size(); i++) {
                if (radioGroup.getCheckedRadioButtonId() == radioGroup.getChildAt(i+1).getId()) {
                    targetFolder = new File(extFoldersList.get(i));
                    break;
                }
            }
        }

        if (null == targetFolder)
        {
            Timber.e("Unknown ID : %s", radioGroup.getCheckedRadioButtonId());
            return;
        }

        targetFolder = new File(targetFolder, subfolderEdit.getText().toString());
        Timber.i("Target : %s", targetFolder.getAbsolutePath());

        String message;
        if (FileHelper.createDirectory(targetFolder)) {
            Timber.i("Target folder created");
            if (FileHelper.isWritable(targetFolder)) {
                Timber.i("Target folder writable");
                message = "Success";
            } else message = "Target folder created but not writable";
        } else message = "Target folder not created";

        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }
}
