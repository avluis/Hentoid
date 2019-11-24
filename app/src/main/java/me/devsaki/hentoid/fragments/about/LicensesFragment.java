package me.devsaki.hentoid.fragments.about;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import me.devsaki.hentoid.R;

import static androidx.core.view.ViewCompat.requireViewById;

public class LicensesFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_licenses, container, false);

        Toolbar toolbar = requireViewById(rootView, R.id.licenses_toolbar);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        WebView webView = requireViewById(rootView, R.id.licenses_web_view);
        webView.loadUrl("file:///android_asset/licenses.html");

        return rootView;
    }
}
