package me.devsaki.hentoid.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.text.InputFilter;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.annimon.stream.function.Consumer;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


public class InputDialog {

    private InputDialog() {
        throw new IllegalStateException("Utility class");
    }


    public static void invokeNumberInputDialog(
            @NonNull final Context context,
            final @StringRes int message,
            @NonNull final Consumer<Integer> onResult) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(Configuration.KEYBOARD_12KEY);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)}); // We don't expect any number longer than 9 chars (999 million)

        DialogInterface.OnClickListener onOk = (dialog, whichButton) -> {
            if (input.getText().length() > 0)
                onResult.accept(Integer.parseInt(input.getText().toString()));

            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(0, 0);
        };

        DialogInterface.OnClickListener onCancel = (dialog, whichButton) -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(0, 0);
        };

        showDialog(context, message, input, onOk, onCancel);
    }

    public static void invokeInputDialog(
            @NonNull final Context context,
            final @StringRes int message,
            @NonNull final Consumer<String> onResult) {
        invokeInputDialog(context, message, null, onResult, null);
    }

    public static void invokeInputDialog(
            @NonNull final Context context,
            final @StringRes int message,
            @Nullable final String text,
            @NonNull final Consumer<String> onResult,
            @Nullable final Runnable onCancelled) {
        EditText input = new EditText(context);
        if (text != null) input.setText(text);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        DialogInterface.OnClickListener onOk = (dialog, whichButton) -> {
            if (input.getText().length() > 0)
                onResult.accept(input.getText().toString().trim());

            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(0, 0);
        };

        DialogInterface.OnClickListener onCancel = (dialog, whichButton) -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(0, 0);
            if (onCancelled != null) onCancelled.run();
        };

        showDialog(context, message, input, onOk, onCancel);
    }

    private static void showDialog(
            @NonNull final Context context,
            final @StringRes int message,
            @NonNull final EditText input,
            @NonNull final DialogInterface.OnClickListener onOk,
            @NonNull final DialogInterface.OnClickListener onCancel
    ) {
        AlertDialog materialDialog = new MaterialAlertDialogBuilder(context)
                .setView(input)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, onOk)
                .setNegativeButton(android.R.string.cancel, onCancel)
                .create();

        materialDialog.show();

        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }
}
