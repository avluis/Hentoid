package me.devsaki.hentoid.fragments.about;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.retrofit.GithubServer;
import me.devsaki.hentoid.viewholders.GitHubRelease;
import timber.log.Timber;

import static android.support.v4.view.ViewCompat.requireViewById;

public class ChangelogFragment extends Fragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private FlexibleAdapter<IFlexible> adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_changelog, container, false);

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
        adapter = new FlexibleAdapter<>(null, null, true);

        RecyclerView recyclerView = requireViewById(rootView, R.id.changelogList);
        recyclerView.setAdapter(adapter);
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

        boolean first = true;
        for (GitHubRelease.Struct r : releasesInfo) {
            GitHubRelease release = new GitHubRelease(r);
            release.setLatest(first);
            releases.add(release);
            first = false;
        }

        adapter.addItems(0, releases);
        // TODO show RecyclerView
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Error fetching GitHub releases data");
        // TODO - don't show recyclerView; show an error message on the entire screen
    }
}
