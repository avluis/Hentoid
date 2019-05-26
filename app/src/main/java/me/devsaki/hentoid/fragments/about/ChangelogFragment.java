package me.devsaki.hentoid.fragments.about;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.IFlexible;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.model.GitHubReleases;
import me.devsaki.hentoid.model.UpdateInfoJson;
import me.devsaki.hentoid.parsers.content.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.GithubServer;
import me.devsaki.hentoid.retrofit.sources.EHentaiServer;
import me.devsaki.hentoid.viewholders.GitHubRelease;
import me.devsaki.hentoid.widget.PrefetchLinearLayoutManager;
import me.devsaki.hentoid.widget.ScrollPositionListener;
import timber.log.Timber;

import static android.support.v4.view.ViewCompat.requireViewById;

public class ChangelogFragment extends Fragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private FlexibleAdapter<IFlexible> adapter;

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

    private void initRecyclerView(View rootView)
    {
        // TODO - invisible init while loading
        adapter = new FlexibleAdapter<>(null);

        RecyclerView recyclerView = requireViewById(rootView, R.id.image_viewer_recycler);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void getReleases()
    {
        compositeDisposable.add(GithubServer.API.getReleases()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onCheckSuccess, this::onCheckError)
        );
    }

    private void onCheckSuccess(GitHubReleases releasesInfo) {
        List<IFlexible> releases = new ArrayList<>();

        for (GitHubReleases.GitHubRelease r : releasesInfo.releases)
            releases.add(new GitHubRelease(r));

        adapter.addItems(0, releases);
        // TODO show RecyclerView
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Error fetching GitHub releases data");
        // TODO - don't show recyclerView; show an error message on the entire screen

    }
}
