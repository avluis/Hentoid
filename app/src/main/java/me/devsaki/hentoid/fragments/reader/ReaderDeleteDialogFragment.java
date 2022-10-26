package me.devsaki.hentoid.fragments.reader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.skydoves.powerspinner.PowerSpinnerView;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.util.Preferences;

public final class ReaderDeleteDialogFragment extends DialogFragment {

    private static final String KEY_DELETE_PAGE_ALLOWED = "delete_page_allowed";

    private Parent parent;
    private boolean isDeletePageAllowed;


    public static void invoke(Fragment parent, boolean isDeletePageAllowed) {
        ReaderDeleteDialogFragment fragment = new ReaderDeleteDialogFragment();

        Bundle args = new Bundle();
        args.putBoolean(KEY_DELETE_PAGE_ALLOWED, isDeletePageAllowed);
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        isDeletePageAllowed = getArguments().getBoolean(KEY_DELETE_PAGE_ALLOWED, false);

        parent = (Parent) getParentFragment();
    }

    @Override
    public void onDestroy() {
        parent = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_reader_delete, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        RadioButton pageBtn = rootView.findViewById(R.id.delete_mode_page);
        RadioButton bookBtn = rootView.findViewById(R.id.delete_mode_book);
        PowerSpinnerView spin = rootView.findViewById(R.id.book_prefs_delete_spin);
        spin.setIsFocusable(true);
        spin.setLifecycleOwner(requireActivity());
        spin.setItems(R.array.page_delete_choices);
        spin.selectItemByIndex(0);

        if (!isDeletePageAllowed) pageBtn.setEnabled(false);

        View okBtn = rootView.findViewById(R.id.action_button);
        okBtn.setOnClickListener(v -> {
            if (!pageBtn.isChecked() && !bookBtn.isChecked()) return;
            Preferences.setViewerDeleteAskMode(spin.getSelectedIndex());
            Preferences.setViewerDeleteTarget(pageBtn.isChecked() ? Preferences.Constant.VIEWER_DELETE_TARGET_PAGE : Preferences.Constant.VIEWER_DELETE_TARGET_BOOK);
            parent.onDeleteElement(pageBtn.isChecked());
            dismiss();
        });
    }

    public interface Parent {
        void onDeleteElement(boolean deletePage);
    }
}
