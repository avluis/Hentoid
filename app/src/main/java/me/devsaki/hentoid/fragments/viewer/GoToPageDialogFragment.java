package me.devsaki.hentoid.fragments.viewer;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;

public final class GoToPageDialogFragment extends DialogFragment {

    private Parent parent;

    public static void show(Fragment parentFragment) {
        GoToPageDialogFragment fragment = new GoToPageDialogFragment();
        fragment.show(parentFragment.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        parent = (Parent) getParentFragment();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);

        DialogInterface.OnClickListener positive = (dialog, whichButton) -> {
            if (input.getText().length() > 0)
                parent.goToPage(Integer.parseInt(input.getText().toString()));
        };

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(input)
                .setPositiveButton(android.R.string.ok, positive)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

        return dialog;
    }

    public interface Parent {
        void goToPage(int pageNum);
    }
}
