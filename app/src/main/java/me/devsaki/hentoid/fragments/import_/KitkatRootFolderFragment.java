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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 08/2019
 * Hentoid folder dialog for kitkat (replaces the SAF directory picker that only exists for Lollipop+)
 */
public class KitkatRootFolderFragment extends DialogFragment {

    // Parent activity to use for callback
    private Parent callbackActivity;

    // List of detected external private folders
    private final List<String> extFoldersList = new ArrayList<>();

    // UI elements
    private EditText subfolderEdit;
    private RadioGroup radioGroup;

    private View publicTxt;
    private View privateImg;
    private View privateTxt;


    public static void invoke(FragmentManager fragmentManager) {
        KitkatRootFolderFragment fragment = new KitkatRootFolderFragment();
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Dialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        try {
            callbackActivity = (Parent) getActivity();
        } catch (ClassCastException e) {
            Timber.e(e, "Calling Activity doesn't implement the Parent interface");
        }

        return inflater.inflate(R.layout.dialog_kitkat_root_folder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        publicTxt = requireViewById(view, R.id.kitkat_root_folder_public_txt);
        privateImg = requireViewById(view, R.id.kitkat_root_folder_private_img);
        privateTxt = requireViewById(view, R.id.kitkat_root_folder_private_txt);
        subfolderEdit = requireViewById(view, R.id.kitkat_root_folder_subfolder);


        String currentFolder = Preferences.getRootFolderName();
        String currentRoot = "";

        radioGroup = requireViewById(view, R.id.kitkat_root_folder_radioGroup);
        radioGroup.setOnCheckedChangeListener((v, w) -> updateDisplayText());

        RadioButton defaultRootBtn = requireViewById(view, R.id.kitkat_btn_default_root);
        File root = Environment.getExternalStorageDirectory();
        defaultRootBtn.setText(formatDirLabel(root, true));
        if (currentFolder.startsWith(root.getAbsolutePath())) {
            currentRoot = root.getAbsolutePath();
            defaultRootBtn.setChecked(true);
        }

        String rootStr;
        // Add detected private external roots
        for (File f : requireContext().getExternalFilesDirs(null)) {
            rootStr = f.getAbsolutePath();
            // Only add if the root is different than Hentoid's default
            // (no point in suggesting a private root in the same volume as the default)
            if (!rootStr.startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                extFoldersList.add(rootStr);
                RadioButton btn = new RadioButton(requireContext());
                btn.setText(formatDirLabel(f, false));
                if (currentFolder.startsWith(rootStr)) {
                    currentRoot = rootStr;
                    btn.setChecked(true);
                }
                radioGroup.addView(btn);
            }
        }

        // Fill subfolder edit
        if (!currentRoot.isEmpty()) {
            currentFolder = currentFolder.replace(currentRoot, ""); // Remove selected root
            currentFolder = currentFolder.replace(Consts.DEFAULT_LOCAL_DIRECTORY, "").replace(Consts.DEFAULT_LOCAL_DIRECTORY_OLD, ""); // Remove Hentoid folder name

            // Remove begin and end /'s
            if (currentFolder.startsWith("/")) currentFolder = currentFolder.substring(1);
            if (currentFolder.endsWith("/"))
                currentFolder = currentFolder.substring(0, currentFolder.length() - 1);

            subfolderEdit.setText(currentFolder);
        }

        View okBtn = requireViewById(view, R.id.kitkat_root_folder_ok);
        okBtn.setOnClickListener(v -> onOkClick());

        updateDisplayText();
    }

    private void updateDisplayText() {
        // Default root
        if (radioGroup.getCheckedRadioButtonId() == R.id.kitkat_btn_default_root) {
            privateImg.setVisibility(View.INVISIBLE);
            privateTxt.setVisibility(View.INVISIBLE);
            publicTxt.setVisibility(View.VISIBLE);
            subfolderEdit.setVisibility(View.VISIBLE);
        } else { // Private external root
            publicTxt.setVisibility(View.GONE);
            privateImg.setVisibility(View.VISIBLE);
            privateTxt.setVisibility(View.VISIBLE);
            subfolderEdit.setVisibility(View.GONE);
        }
    }

    private String formatDirLabel(@NonNull File f, boolean isDefault) {
        FileHelper.MemoryUsageFigures mem = new FileHelper.MemoryUsageFigures(f);
        String label = requireContext().getResources().getString(R.string.kitkat_dialog_dir);
        label = label.replace("$dir", f.getAbsolutePath() + "/");
        label = label.replace("$default", isDefault ? "(Default)" : "");
        label = label.replace("$freeUsage", mem.formatFreeUsageMb());
        return label.replace("$freePc", Long.toString(Math.round(mem.getFreeUsageRatio100())));
    }

    private void onOkClick() {
        File targetFolder = null;
        String subfolder = "";

        // Default root
        if (radioGroup.getCheckedRadioButtonId() == R.id.kitkat_btn_default_root) {
            targetFolder = Environment.getExternalStorageDirectory();
            subfolder = subfolderEdit.getText().toString().trim();
            // Remove spaces added around /'s by dumb phone keyboards
            subfolder = subfolder.replace(" /", "/").replace("/ ", "/");
        } else { // Private external root
            for (int i = 0; i < extFoldersList.size(); i++) {
                if (radioGroup.getCheckedRadioButtonId() == radioGroup.getChildAt(i + 1).getId()) {
                    targetFolder = new File(extFoldersList.get(i));
                    break;
                }
            }
        }

        if (null == targetFolder) {
            Timber.e("Unknown ID : %s", radioGroup.getCheckedRadioButtonId());
            return;
        }

        targetFolder = new File(targetFolder, subfolder);
        Timber.i("Target : %s", targetFolder.getAbsolutePath());

        callbackActivity.onSelectKitKatRootFolder(targetFolder);
        dismiss();
    }

    public interface Parent {
        void onSelectKitKatRootFolder(File targetFolder);
    }
}
