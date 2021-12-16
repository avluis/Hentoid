package me.devsaki.hentoid.fragments.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import me.devsaki.hentoid.json.GithubRelease;
import me.devsaki.hentoid.retrofit.GithubServer;
import me.devsaki.hentoid.viewholders.GitHubReleaseItem;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;

/**
 * Launcher dialog for the library refresh feature
 */
public class UpdateSuccessDialogFragment extends DialogFragment {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final ItemAdapter<GitHubReleaseItem> itemAdapter = new ItemAdapter<>();

    public static void invoke(FragmentManager fragmentManager) {
        UpdateSuccessDialogFragment fragment = new UpdateSuccessDialogFragment();
        fragment.show(fragmentManager, "usdf");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.dialog_library_update_success, container, false);

        FastAdapter<GitHubReleaseItem> releaseItemAdapter = FastAdapter.with(itemAdapter);
        RecyclerView releaseItem = requireViewById(rootView, R.id.changelogReleaseItem);
        releaseItem.setAdapter(releaseItemAdapter);

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
        compositeDisposable.add(GithubServer.API.getLatestRelease() // No need to run on BG thread; retrofit already makes async calls
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onCheckSuccess, this::onCheckError)
        );
    }

    private void onCheckSuccess(GithubRelease latestReleaseInfo) {
        itemAdapter.add(new GitHubReleaseItem(latestReleaseInfo));
    }

    private void onCheckError(Throwable t) {
        Timber.w(t, "Error fetching GitHub latest release data");
    }
}
