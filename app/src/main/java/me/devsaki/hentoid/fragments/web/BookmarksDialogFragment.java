package me.devsaki.hentoid.fragments.web;

import static androidx.core.view.ViewCompat.requireViewById;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.material.button.MaterialButton;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewholders.TextItem;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;

public final class BookmarksDialogFragment extends DialogFragment implements ItemTouchCallback {

    private static final String KEY_SITE = "site";
    private static final String KEY_TITLE = "title";
    private static final String KEY_URL = "url";

    // === UI
    private Toolbar selectionToolbar;
    private MenuItem editMenu;
    private MenuItem copyMenu;
    private MenuItem homeMenu;
    private RecyclerView recyclerView;
    private MaterialButton bookmarkCurrentBtn;

    private final ItemAdapter<TextItem<SiteBookmark>> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<TextItem<SiteBookmark>> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<TextItem<SiteBookmark>> selectExtension;
    private ItemTouchHelper touchHelper;

    // === VARIABLES
    private Parent parent;
    private Site site;
    private String title;
    private String url;
    // Bookmark ID of the current webpage
    private long bookmarkId = -1;

    private Disposable disposable;

    // Used to ignore native calls to onBookClick right after that book has been deselected
    private boolean invalidateNextBookClick = false;


    public static void invoke(
            @NonNull final FragmentActivity parent,
            @NonNull final Site site,
            @NonNull final String title,
            @NonNull final String url) {
        BookmarksDialogFragment fragment = new BookmarksDialogFragment();

        Bundle args = new Bundle();
        args.putInt(KEY_SITE, site.getCode());
        args.putString(KEY_TITLE, title);
        args.putString(KEY_URL, url);
        fragment.setArguments(args);

        fragment.show(parent.getSupportFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        site = Site.searchByCode(getArguments().getInt(KEY_SITE));
        title = getArguments().getString(KEY_TITLE, "");
        url = getArguments().getString(KEY_URL, "");

        parent = (Parent) getActivity();
    }

    @Override
    public void onDestroy() {
        parent = null;
        disposable = Completable.fromRunnable(() -> {
                    Context context = bookmarkCurrentBtn.getContext();
                    CollectionDAO dao = new ObjectBoxDAO(context);
                    try {
                        Helper.updateBookmarksJson(context, dao);
                    } finally {
                        dao.cleanup();
                    }
                }
        ).subscribeOn(Schedulers.io())
                .subscribe(() -> {
                    if (disposable != null) disposable.dispose();
                });
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

        MaterialButton homepage = requireViewById(rootView, R.id.bookmark_homepage_btn);
        homepage.setIcon(ContextCompat.getDrawable(requireContext(), site.getIco()));
        homepage.setOnClickListener(v -> parent.loadUrl(site.getUrl()));

        List<SiteBookmark> bookmarks = reloadBookmarks();

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());

            FastAdapterPreClickSelectHelper<TextItem<SiteBookmark>> helper = new FastAdapterPreClickSelectHelper<>(selectExtension);
            fastAdapter.setOnPreClickListener(helper::onPreClickListener);
            fastAdapter.setOnPreLongClickListener(helper::onPreLongClickListener);
        }

        recyclerView = requireViewById(rootView, R.id.bookmarks_list);

        // Activate drag & drop
        SimpleDragCallback dragCallback = new SimpleDragCallback(this);
        dragCallback.setNotifyAllDrops(true);
        touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(recyclerView);

        recyclerView.setAdapter(fastAdapter);

        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(i));
        fastAdapter.addEventHook(
                new TextItem.DragHandlerTouchEvent<>(
                        position -> {
                            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
                            if (vh != null) touchHelper.startDrag(vh);
                        }
                )
        );

        selectionToolbar = requireViewById(rootView, R.id.toolbar);
        selectionToolbar.setOnMenuItemClickListener(this::selectionToolbarOnItemClicked);
        editMenu = selectionToolbar.getMenu().findItem(R.id.action_edit);
        copyMenu = selectionToolbar.getMenu().findItem(R.id.action_copy);
        homeMenu = selectionToolbar.getMenu().findItem(R.id.action_home);

        bookmarkCurrentBtn = requireViewById(rootView, R.id.bookmark_current_btn);
        Optional<SiteBookmark> currentBookmark = Stream.of(bookmarks).filter(b -> SiteBookmark.urlsAreSame(b.getUrl(), url)).findFirst();
        if (currentBookmark.isPresent()) bookmarkId = currentBookmark.get().id;
        updateBookmarkButton();
    }

    private List<SiteBookmark> reloadBookmarks() {
        List<SiteBookmark> bookmarks;
        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        try {
            bookmarks = reloadBookmarks(dao);
        } finally {
            dao.cleanup();
        }
        return bookmarks;
    }

    private List<SiteBookmark> reloadBookmarks(CollectionDAO dao) {
        List<SiteBookmark> bookmarks;
        bookmarks = dao.selectBookmarks(site);
        itemAdapter.set(Stream.of(bookmarks).map(b -> new TextItem<>(b.getTitle(), b, true, true, b.isHomepage(), touchHelper)).toList());
        return bookmarks;
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
            new Handler(Looper.getMainLooper()).postDelayed(() -> invalidateNextBookClick = false, 200);
        } else {
            editMenu.setVisible(1 == selectedCount);
            copyMenu.setVisible(1 == selectedCount);
            homeMenu.setVisible(1 == selectedCount);
            selectionToolbar.setVisibility(View.VISIBLE);
        }
    }

    private void updateBookmarkButton() {
        Context context = bookmarkCurrentBtn.getContext();
        if (bookmarkId > -1) {
            bookmarkCurrentBtn.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark_full));
            bookmarkCurrentBtn.setText(R.string.unbookmark_current);
            bookmarkCurrentBtn.setOnClickListener(v -> onBookmarkBtnClickedRemove());
            parent.updateBookmarkButton(true);
        } else {
            bookmarkCurrentBtn.setIcon(ContextCompat.getDrawable(context, R.drawable.ic_bookmark));
            bookmarkCurrentBtn.setText(R.string.bookmark_current);
            bookmarkCurrentBtn.setOnClickListener(v -> onBookmarkBtnClickedAdd());
            parent.updateBookmarkButton(false);
        }
    }

    private void onBookmarkBtnClickedAdd() {
        CollectionDAO dao = new ObjectBoxDAO(bookmarkCurrentBtn.getContext());
        try {
            bookmarkId = dao.insertBookmark(new SiteBookmark(site, title, url));
            reloadBookmarks(dao);
            fastAdapter.notifyAdapterDataSetChanged();
        } finally {
            dao.cleanup();
        }
        updateBookmarkButton();
    }

    private void onBookmarkBtnClickedRemove() {
        CollectionDAO dao = new ObjectBoxDAO(bookmarkCurrentBtn.getContext());
        try {
            dao.deleteBookmark(bookmarkId);
            bookmarkId = -1;
            reloadBookmarks(dao);
            fastAdapter.notifyAdapterDataSetChanged();
        } finally {
            dao.cleanup();
        }
        updateBookmarkButton();
    }

    @SuppressLint("NonConstantResourceId")
    private boolean selectionToolbarOnItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_copy:
                copySelectedItem();
                break;
            case R.id.action_edit:
                editSelectedItem();
                break;
            case R.id.action_delete:
                purgeSelectedItems();
                break;
            case R.id.action_home:
                toggleHomeSelectedItem();
                break;
            default:
                selectionToolbar.setVisibility(View.GONE);
                return false;
        }
        return true;
    }

    /**
     * Callback for the "share item" action button
     */
    private void copySelectedItem() {
        Set<TextItem<SiteBookmark>> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            SiteBookmark b = Stream.of(selectedItems).findFirst().get().getTag();
            if (b != null && Helper.copyPlainTextToClipboard(context, b.getUrl())) {
                ToastHelper.toast(context, R.string.web_url_clipboard);
                selectionToolbar.setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * Callback for the "share item" action button
     */
    private void editSelectedItem() {
        Set<TextItem<SiteBookmark>> selectedItems = selectExtension.getSelectedItems();
        if (1 == selectedItems.size()) {
            SiteBookmark b = Stream.of(selectedItems).findFirst().get().getTag();
            if (b != null)
                InputDialog.invokeInputDialog(requireActivity(), R.string.bookmark_edit_title, b.getTitle(),
                        this::onEditTitle, () -> selectExtension.deselect(selectExtension.getSelections()));
        }
    }

    private void onEditTitle(@NonNull final String newTitle) {
        Set<TextItem<SiteBookmark>> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            SiteBookmark b = Stream.of(selectedItems).findFirst().get().getTag();
            if (b != null) {
                b.setTitle(newTitle);
                CollectionDAO dao = new ObjectBoxDAO(context);
                try {
                    dao.insertBookmark(b);
                    reloadBookmarks(dao);
                    fastAdapter.notifyAdapterDataSetChanged();
                    selectionToolbar.setVisibility(View.INVISIBLE);
                } finally {
                    dao.cleanup();
                }
            }
        }
    }

    /**
     * Callback for the "delete item" action button
     */
    private void purgeSelectedItems() {
        Set<TextItem<SiteBookmark>> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (!selectedItems.isEmpty() && context != null) {
            List<SiteBookmark> selectedContent = Stream.of(selectedItems).map(TextItem::getTag).withoutNulls().toList();
            if (!selectedContent.isEmpty()) {
                CollectionDAO dao = new ObjectBoxDAO(context);
                try {
                    for (SiteBookmark b : selectedContent) {
                        if (b.id == bookmarkId) {
                            bookmarkId = -1;
                            updateBookmarkButton();
                        }
                        dao.deleteBookmark(b.id);
                    }
                    reloadBookmarks(dao);
                    fastAdapter.notifyAdapterDataSetChanged();
                    selectionToolbar.setVisibility(View.INVISIBLE);
                } finally {
                    dao.cleanup();
                }
            }
        }
    }

    /**
     * Callback for the "toggle as welcome page" action button
     */
    private void toggleHomeSelectedItem() {
        Set<TextItem<SiteBookmark>> selectedItems = selectExtension.getSelectedItems();
        Context context = getActivity();
        if (1 == selectedItems.size() && context != null) {
            List<SiteBookmark> selectedContent = Stream.of(selectedItems).map(TextItem::getTag).withoutNulls().toList();
            if (!selectedContent.isEmpty()) {
                SiteBookmark selectedBookmark = selectedContent.get(0);
                CollectionDAO dao = new ObjectBoxDAO(context);
                try {
                    List<SiteBookmark> bookmarks = dao.selectBookmarks(site);
                    for (SiteBookmark b : bookmarks) {
                        if (b.id == selectedBookmark.id) b.setHomepage(!b.isHomepage());
                        else b.setHomepage(false);
                    }
                    dao.insertBookmarks(bookmarks);
                    reloadBookmarks(dao);
                    fastAdapter.notifyAdapterDataSetChanged();

                    selectExtension.setSelectOnLongClick(true);
                    selectExtension.deselect(selectExtension.getSelections());
                    selectionToolbar.setVisibility(View.INVISIBLE);
                } finally {
                    dao.cleanup();
                }
            }
        }
    }

    private boolean onItemClick(TextItem<SiteBookmark> item) {
        if (null == selectExtension) return false;

        if (selectExtension.getSelectedItems().isEmpty()) {
            if (!invalidateNextBookClick && item.getTag() != null) {
                parent.loadUrl(item.getTag().getUrl());
                this.dismiss();
            } else invalidateNextBookClick = false;

            return true;
        } else {
            selectExtension.setSelectOnLongClick(false);
        }
        return false;
    }

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        // Update  visuals
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(newPosition);
        if (vh instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) vh).onDropped();
        }

        // Update DB
        if (oldPosition == newPosition) return;

        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        try {
            List<SiteBookmark> bookmarks = dao.selectBookmarks(site);
            if (oldPosition < 0 || oldPosition >= bookmarks.size()) return;

            // Move the item
            SiteBookmark fromValue = bookmarks.get(oldPosition);
            int delta = oldPosition < newPosition ? 1 : -1;
            for (int i = oldPosition; i != newPosition; i += delta) {
                bookmarks.set(i, bookmarks.get(i + delta));
            }
            bookmarks.set(newPosition, fromValue);

            // Renumber everything and update the DB
            int index = 1;
            for (SiteBookmark b : bookmarks) {
                b.setOrder(index++);
                dao.insertBookmark(b);
            }
        } finally {
            dao.cleanup();
        }
    }

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        // Update visuals
        DragDropUtil.onMove(itemAdapter, oldPosition, newPosition); // change position
        return true;
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Update visuals
        if (viewHolder instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) viewHolder).onDragged();
        }
    }

    @Override
    public void itemTouchStopDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing
    }

    public interface Parent {
        void loadUrl(@NonNull final String url);

        void updateBookmarkButton(boolean newValue);
    }
}
