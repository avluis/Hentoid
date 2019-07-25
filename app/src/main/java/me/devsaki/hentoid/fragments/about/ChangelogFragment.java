package me.devsaki.hentoid.fragments.about;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.retrofit.GithubServer;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.services.UpdateDownloadService;
import me.devsaki.hentoid.viewholders.GitHubRelease;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

public class ChangelogFragment extends Fragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private FlexibleAdapter<IFlexible> changelogAdapter;

    // Download bar
    private TextView downloadLatestText;
    private View downloadLatestButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_changelog, container, false);

        downloadLatestText = requireViewById(rootView, R.id.changelogDownloadLatestText);
        downloadLatestButton = requireViewById(rootView, R.id.changelogDownloadLatestButton);
        downloadLatestText.setOnClickListener(this::onDownloadClick);
        downloadLatestButton.setOnClickListener(this::onDownloadClick);

        initRecyclerView(rootView);
        getReleases();

        return rootView;
    }

    @Override
    public void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void initRecyclerView(View rootView) {
        // TODO - invisible init while loading
        changelogAdapter = new FlexibleAdapter<>(null, null, true);

        RecyclerView recyclerView = requireViewById(rootView, R.id.changelogList);
        recyclerView.setAdapter(changelogAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void getReleases() {
        compositeDisposable.add(GithubServer.API.getReleases()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onCheckSuccess, this::onCheckError)
        );
    }

    private void onCheckSuccess(List<GitHubRelease.Struct> releasesInfo) {
        List<IFlexible> releases = new ArrayList<>();

        String latestTagName = "";
        for (GitHubRelease.Struct r : releasesInfo) {
            GitHubRelease release = new GitHubRelease(r);
            if (release.isTagPrior(BuildConfig.VERSION_NAME)) releases.add(release);
            if (latestTagName.isEmpty()) latestTagName = release.getTagName();
        }

        changelogAdapter.addItems(0, releases);
        if (releasesInfo.size() > releases.size()) enableDownloadBar(latestTagName);
        // TODO show RecyclerView
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Error fetching GitHub releases data");
        // TODO - don't show recyclerView; show an error message on the entire screen
    }

    private void enableDownloadBar(String latestTagName) {
        downloadLatestText.setText(downloadLatestText.getContext().getString(R.string.get_latest).replace("@v", latestTagName));
        downloadLatestText.setVisibility(View.VISIBLE);
        downloadLatestButton.setVisibility(View.VISIBLE);
    }

    private void onDownloadClick(View v) {
        // Equivalent to "check for updates" preferences menu
        if (!UpdateDownloadService.isRunning()) {
            Intent intent = UpdateCheckService.makeIntent(requireContext(), true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent);
            } else {
                requireContext().startService(intent);
            }
        }
    }

}
