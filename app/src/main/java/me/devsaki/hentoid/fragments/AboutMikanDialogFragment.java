package me.devsaki.hentoid.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.webkit.WebView;

public final class AboutMikanDialogFragment extends DialogFragment {

    public static void show(FragmentManager fragmentManager) {
        AboutMikanDialogFragment fragment = new AboutMikanDialogFragment();
        fragment.show(fragmentManager, null);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        WebView webView = new WebView(requireContext());
        webView.loadUrl("file:///android_asset/about_mikan.html");
        webView.setInitialScale(95);

        return new android.support.v7.app.AlertDialog.Builder(requireContext())
                .setTitle("About Mikan Search")
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
