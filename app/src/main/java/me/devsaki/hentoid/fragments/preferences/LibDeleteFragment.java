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

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import timber.log.Timber;

/**
 * Created by Robb on 11/2018
 * Launcher dialog for the library refresh feature
 */
public class LibDeleteFragment extends DialogFragment {

    private static final String BOOK_LIST = "book_list";

    private long[] booksList;

    private TextView progressTxt;
    private ProgressBar progressBar;

    private CollectionDAO dao;
    private Disposable searchDisposable = Disposables.empty();


    public static void invoke(@NonNull final FragmentManager fragmentManager, @NonNull final List<Long> bookList) {
        LibDeleteFragment fragment = new LibDeleteFragment();

        Bundle args = new Bundle();
        args.putLongArray(BOOK_LIST, Helper.getPrimitiveLongArrayFromList(bookList));
        fragment.setArguments(args);

        fragment.show(fragmentManager, null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        booksList = getArguments().getLongArray(BOOK_LIST);
    }

    @Override
    public void onDestroyView() {
        if (searchDisposable != null) searchDisposable.dispose();
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

        progressBar.setMax(booksList.length);

        dao = new ObjectBoxDAO(getActivity());
        searchDisposable = Observable.fromIterable(Helper.getListFromPrimitiveArray(booksList))
                .observeOn(Schedulers.io())
                .map(id -> dao.selectContent(id))
                .map(this::deleteItem)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::next,
                        Timber::w,
                        this::finish
                );
    }

    private boolean deleteItem(@NonNull Content c) {
        ContentHelper.removeContent(requireActivity(), c, dao);
        return true;
    }

    private void next(boolean b) {
        int currentProgress = progressBar.getProgress() + 1;
        progressTxt.setText(getString(R.string.book_progress, currentProgress, progressBar.getMax()));
        progressBar.setProgress(currentProgress);
    }

    private void finish() {
        searchDisposable.dispose();
        dismiss();
    }
}
