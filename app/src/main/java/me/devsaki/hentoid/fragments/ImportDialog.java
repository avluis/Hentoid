package me.devsaki.hentoid.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import me.devsaki.hentoid.R;

/**
 * Created by avluis on 04/02/2016.
 * Presents import progress as an AlertDialog
 */
public class ImportDialog extends DialogFragment {

    private Activity activity;
    private LayoutInflater inflater;

    private TextView tvHeader;
    private TextView tvProgress;
    private ProgressBar progressBar;

    private String mHeader;
    private String mProgress;
    static String headerKey = "header";
    static String progressKey = "progress";

    public static ImportDialog getInstance(String header, String progress) {
        ImportDialog dialog = new ImportDialog();
        Bundle bundle = new Bundle();
        bundle.putString(headerKey, header);
        bundle.putString(progressKey, progress);
        dialog.setArguments(bundle);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_import_library, null);

        initializeDialog(view);

        return new AlertDialog.Builder(activity, R.style.AppCompatAlertDialog)
                .setTitle("Please wait...")
                .setCancelable(false)
                .setView(view)
                .create();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.activity = getActivity();
        this.inflater = activity.getLayoutInflater();
        Bundle bundle = getArguments();
        mHeader = bundle.getString(headerKey);
        mProgress = bundle.getString(progressKey);
    }

    private void initializeDialog(View rootView) {
        tvHeader = (TextView) rootView.findViewById(R.id.tv_dialog_header);
        tvHeader.setText(mHeader);

        tvProgress = (TextView) rootView.findViewById(R.id.tv_dialog_progress);
        tvProgress.setText(mProgress);

        progressBar = (ProgressBar) rootView.findViewById(R.id.pb_dialog);
    }
}