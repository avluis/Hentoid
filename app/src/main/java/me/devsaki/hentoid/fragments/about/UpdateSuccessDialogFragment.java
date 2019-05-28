package me.devsaki.hentoid.fragments.about;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.retrofit.GithubServer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.viewholders.GitHubRelease;
import timber.log.Timber;

import static android.support.v4.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class UpdateSuccessDialogFragment extends DialogFragment {

    private static int DP_8;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private TextView releaseName;
    private LinearLayout releaseDescription;

    public static void invoke(FragmentManager fragmentManager) {
        UpdateSuccessDialogFragment fragment = new UpdateSuccessDialogFragment();
        fragment.setStyle(DialogFragment.STYLE_NO_FRAME, R.style.DownloadsDialog);
        fragment.show(fragmentManager, null);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.dialog_update_success, container, false);

        releaseName = requireViewById(rootView, R.id.changelogReleaseTitle);
        releaseDescription = requireViewById(rootView, R.id.changelogReleaseDescription);
        DP_8 = Helper.dpToPixel(requireContext(), 8);

        getReleases();

        return rootView;
    }

    @Override
    public void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    private void getReleases() {
        compositeDisposable.add(GithubServer.API.getLatestRelease()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onCheckSuccess, this::onCheckError)
        );
    }

    private void onCheckSuccess(GitHubRelease.Struct latestReleaseInfo) {
        releaseName.setText(latestReleaseInfo.name);
        // Parse content and add lines to the description
        for (String s : latestReleaseInfo.body.split("\\r\\n")) {
            s = s.trim();
            if (s.startsWith("-")) addListContent(s);
            else addDescContent(s);
        }
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Error fetching GitHub latest release data");
    }

    void addDescContent(String text) {
        TextView tv = new TextView(releaseDescription.getContext());
        tv.setText(text);
        tv.setPadding(0, DP_8, 0, 0);
        releaseDescription.addView(tv);
    }

    void addListContent(String text) {
        TextView tv = new TextView(releaseDescription.getContext());
        tv.setText(text);
        tv.setPadding(DP_8 * 2, DP_8, 0, 0);
        releaseDescription.addView(tv);
    }
}
