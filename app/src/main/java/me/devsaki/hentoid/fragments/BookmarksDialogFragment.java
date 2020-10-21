package me.devsaki.hentoid.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.button.MaterialButton;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.select.SelectExtension;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.viewholders.TextItem;

import static androidx.core.view.ViewCompat.requireViewById;

public final class BookmarksDialogFragment extends DialogFragment {

    private static final String URL = "url";
    private static final String SITE = "site";

    // === UI
    private Toolbar selectionToolbar;
    private SelectExtension<TextItem<SiteBookmark>> selectExtension;
    private MaterialButton bookmarkCurrentBtn;

    // === VARIABLES
    private Parent parent;
    private String url;
    private Site site;

    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;


    public static void invoke(
            @NonNull final FragmentActivity parent,
            @NonNull final Site site,
            @NonNull final String url) {
        BookmarksDialogFragment fragment = new BookmarksDialogFragment();

        Bundle args = new Bundle();
        args.putString(URL, url);
        args.putInt(SITE, site.getCode());
        fragment.setArguments(args);

        fragment.show(parent.getSupportFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        url = getArguments().getString(URL, "");
        site = Site.searchByCode(getArguments().getInt(SITE));
        parent = (Parent) getParentFragment();
    }

    @Override
    public void onDestroy() {
        parent = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        return inflater.inflate(R.layout.dialog_web_bookmarks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        ItemAdapter<TextItem<SiteBookmark>> itemAdapter = new ItemAdapter<>();

        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        try {
            List<SiteBookmark> bookmarks = dao.getBookmarks(site);
            itemAdapter.set(Stream.of(bookmarks).map(s -> new TextItem<>(s.getTitle(), s, false, true)).toList());

            FastAdapter<TextItem<SiteBookmark>> fastAdapter = FastAdapter.with(itemAdapter);
            fastAdapter.setOnClickListener((v, a, i, p) -> onItemSelected(i.getTag()));

            // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
            selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
            if (selectExtension != null) {
                selectExtension.setSelectable(true);
                selectExtension.setMultiSelect(true);
                selectExtension.setSelectOnLongClick(true);
                selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());
            }

            RecyclerView sitesRecycler = requireViewById(rootView, R.id.bookmarks_list);
            sitesRecycler.setAdapter(fastAdapter);

            selectionToolbar = requireViewById(rootView, R.id.toolbar);
            selectionToolbar.setOnMenuItemClickListener(this::selectionToolbarOnItemClicked);

            bookmarkCurrentBtn = requireViewById(rootView, R.id.bookmark_current_btn);
        } finally {
            dao.cleanup();
        }
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        int selectedCount = selectExtension.getSelectedItems().size();

        if (0 == selectedCount) {
            selectionToolbar.setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
            invalidateNextBookClick = true;
            new Handler().postDelayed(() -> invalidateNextBookClick = false, 200);
        } else {
            selectionToolbar.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("NonConstantResourceId")
    private boolean selectionToolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_edit:
                //shareSelectedItems();
                break;
            case R.id.action_delete:
                //purgeSelectedItems();
                break;
            default:
                selectionToolbar.setVisibility(View.GONE);
                return false;
        }
        return true;
    }

    private boolean onItemSelected(SiteBookmark bookmark) {
        if (null == bookmark) return false;

        parent.openUrl(bookmark.getUrl());

        this.dismiss();
        return true;
    }

    public interface Parent {
        void openUrl(@NonNull final String url);
    }
}
