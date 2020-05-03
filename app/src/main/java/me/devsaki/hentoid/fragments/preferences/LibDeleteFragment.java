package me.devsaki.hentoid.fragments.preferences;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import timber.log.Timber;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class LibDeleteFragment extends DialogFragment {

    private TextView progressTxt;
    private ProgressBar progressBar;

    private CollectionDAO dao;
    private Disposable searchDisposable = Disposables.empty();


    public static void invoke(@NonNull final FragmentManager fragmentManager) {
        LibDeleteFragment fragment = new LibDeleteFragment();

        fragment.show(fragmentManager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setCancelable(false);
        return inflater.inflate(R.layout.dialog_prefs_delete, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        progressTxt = rootView.findViewById(R.id.delete_progress_test);
        progressBar = rootView.findViewById(R.id.delete_bar);

        dao = new ObjectBoxDAO(getActivity());
        searchDisposable = dao.getStoredBookIds(true, false)
                .doOnSuccess(list -> {
                    Timber.i(">> onSuccess %s", list.size()); // TODO temp
                    progressBar.setMax(list.size());
                })
                .observeOn(Schedulers.io())
                .flattenAsObservable(id1 -> id1)
                .map(id2 -> {
                    Timber.i(">> select %s", id2); // TODO temp
                    return dao.selectContent(id2);
                })
                .map(this::deleteItem)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::next,
                        Timber::w,
                        this::finish
                );
    }

    private boolean deleteItem(@NonNull Content c) {
        Timber.i(">> delete"); // TODO temp
        ContentHelper.removeContent(requireActivity(), c, dao);
        return true;
    }

    private void next(boolean b) {
        int currentProgress = progressBar.getProgress() + 1;
        Timber.i(">> next %s", currentProgress); // TODO temp
        progressTxt.setText(getString(R.string.delete_progress, currentProgress, progressBar.getMax()));
        progressBar.setProgress(currentProgress);
    }

    private void finish() {
        Timber.i(">> finish"); // TODO temp
        searchDisposable.dispose();
        dismiss();
    }
}
