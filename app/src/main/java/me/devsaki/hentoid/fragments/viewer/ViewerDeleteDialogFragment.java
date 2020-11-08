package me.devsaki.hentoid.fragments.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;

public final class ViewerDeleteDialogFragment extends DialogFragment {

    private static final String KEY_DELETE_PAGE_ALLOWED = "delete_page_allowed";

    private Parent parent;
    private boolean isDeletePageAllowed;


    public static void invoke(Fragment parent, boolean isDeletePageAllowed) {
        ViewerDeleteDialogFragment fragment = new ViewerDeleteDialogFragment();

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
        return inflater.inflate(R.layout.dialog_viewer_delete, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        RadioButton pageBtn = rootView.findViewById(R.id.delete_mode_page);
        RadioButton bookBtn = rootView.findViewById(R.id.delete_mode_book);

        if (!isDeletePageAllowed) pageBtn.setEnabled(false);

        View okBtn = rootView.findViewById(R.id.book_delete_ok_btn);
        okBtn.setOnClickListener(v -> {
            if (!pageBtn.isChecked() && !bookBtn.isChecked()) return;
            parent.onDeleteElement(pageBtn.isChecked());
            dismiss();
        });
    }

    public interface Parent {
        void onDeleteElement(boolean deletePage);
    }
}
