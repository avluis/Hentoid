package me.devsaki.hentoid.fragments.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.retrofit.GithubServer;
import me.devsaki.hentoid.viewholders.GitHubReleaseDescItem;
import me.devsaki.hentoid.viewholders.GitHubReleaseItem;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class UpdateSuccessDialogFragment extends DialogFragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private TextView releaseName;
    private ItemAdapter<GitHubReleaseDescItem> itemAdapter = new ItemAdapter<>();

    public static void invoke(FragmentManager fragmentManager) {
        UpdateSuccessDialogFragment fragment = new UpdateSuccessDialogFragment();
        fragment.show(fragmentManager, "usdf");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.dialog_library_update_success, container, false);

        releaseName = requireViewById(rootView, R.id.changelogReleaseTitle);

        FastAdapter<GitHubReleaseDescItem> releaseDescriptionAdapter = FastAdapter.with(itemAdapter);
        RecyclerView releaseDescription = requireViewById(rootView, R.id.changelogReleaseDescription);
        releaseDescription.setAdapter(releaseDescriptionAdapter);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getReleases();
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

    private void onCheckSuccess(GitHubReleaseItem.Struct latestReleaseInfo) {
        releaseName.setText(latestReleaseInfo.getName());
        // Parse content and add lines to the description
        for (String s : latestReleaseInfo.getBody().split("\\r\\n")) { // TODO - refactor this code with its copy in GitHubRelease
            s = s.trim();
            if (s.startsWith("-")) addListContent(s);
            else addDescContent(s);
        }
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Error fetching GitHub latest release data");
    }

    private void addDescContent(String text) {
        itemAdapter.add(new GitHubReleaseDescItem(text, GitHubReleaseDescItem.Type.DESCRIPTION));
    }

    private void addListContent(String text) {
        itemAdapter.add(new GitHubReleaseDescItem(text, GitHubReleaseDescItem.Type.LIST_ITEM));
    }
}
