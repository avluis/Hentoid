package me.devsaki.hentoid.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.text.InputType;
import android.view.WindowManager;
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

        DialogInterface.OnClickListener positive = (dialog, whichButton) -> {
            if (input.getText().length() > 0)
                onResult.accept(Integer.parseInt(input.getText().toString()));
        };

        showDialog(context, message, input, positive);
    }

    public static void invokeInputDialog(
            @NonNull final Context context,
            final @StringRes int message,
            @NonNull final Consumer<String> onResult) {
        invokeInputDialog(context, message, null, onResult);
    }

    public static void invokeInputDialog(
            @NonNull final Context context,
            final @StringRes int message,
             @Nullable final String text,
            @NonNull final Consumer<String> onResult) {
        EditText input = new EditText(context);
        if (text != null) input.setText(text);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
//        input.setRawInputType(Configuration.);

        DialogInterface.OnClickListener positive = (dialog, whichButton) -> {
            if (input.getText().length() > 0)
                onResult.accept(input.getText().toString().trim());
        };

        showDialog(context, message, input, positive);
    }

    private static void showDialog(
            @NonNull final Context context,
            final @StringRes int message,
            @NonNull final EditText input,
            @NonNull final DialogInterface.OnClickListener positive
    ) {
        AlertDialog materialDialog = new MaterialAlertDialogBuilder(context)
                .setView(input)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, positive)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        materialDialog.show();
        materialDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }
}
