package me.devsaki.hentoid.fragments.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.IntStream;
import com.annimon.stream.Stream;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.databinding.DialogLibrarySplitBinding;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewholders.TextItem;
import me.devsaki.hentoid.widget.DragSelectTouchListener;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;

public final class SplitDialogFragment extends DialogFragment implements ItemTouchCallback {

    private static final String KEY_CONTENT = "content";

    // === UI
    private DialogLibrarySplitBinding binding = null;

    private final ItemAdapter<TextItem<Chapter>> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<TextItem<Chapter>> fastAdapter = FastAdapter.with(itemAdapter);
    private SelectExtension<TextItem<Chapter>> selectExtension;

    private DragSelectTouchListener mDragSelectTouchListener;

    // === VARIABLES
    private Parent parent;
    private long contentId;
    private Content content;


    public static void invoke(
            @NonNull final Fragment parent,
            @NonNull final Content content) {
        SplitDialogFragment fragment = new SplitDialogFragment();

        Bundle args = new Bundle();
        args.putLong(KEY_CONTENT, content.getId());
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        contentId = getArguments().getLong(KEY_CONTENT);

        parent = (Parent) getParentFragment();
    }

    @Override
    public void onDestroy() {
        parent = null;
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        binding = DialogLibrarySplitBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());

            FastAdapterPreClickSelectHelper<TextItem<Chapter>> helper = new FastAdapterPreClickSelectHelper<>(selectExtension);
            fastAdapter.setOnPreClickListener(helper::onPreClickListener);
            fastAdapter.setOnPreLongClickListener((v, a, i, p) -> {
                // Warning : specific code for drag selection
                mDragSelectTouchListener.startDragSelection(p);
                return helper.onPreLongClickListener(v, a, i, p);
            });
        }
        binding.list.setAdapter(fastAdapter);

        // Select on swipe
        DragSelectTouchListener.OnDragSelectListener onDragSelectionListener = (start, end, isSelected) -> selectExtension.select(IntStream.rangeClosed(start, end).boxed().toList());
        mDragSelectTouchListener = new DragSelectTouchListener()
                .withSelectListener(onDragSelectionListener);
        binding.list.addOnItemTouchListener(mDragSelectTouchListener);

        binding.nochapterAction.setOnClickListener(v -> onCreateChaptersClick());
        binding.actionButton.setOnClickListener(v -> onActionClick());
    }

    @Override
    public void onResume() {
        super.onResume();
        List<Chapter> chapterList = loadChapterList();
        itemAdapter.set(Stream.of(chapterList).map(c -> new TextItem<>(c.getName(), c, false, false, false, null)).toList());

        // Display help text is no chapters
        if (chapterList.isEmpty()) {
            binding.nochapterView.setVisibility(View.VISIBLE);
            binding.list.setVisibility(View.GONE);
        } else {
            binding.nochapterView.setVisibility(View.GONE);
            binding.list.setVisibility(View.VISIBLE);
        }
    }

    private List<Chapter> loadChapterList() {
        List<Chapter> result;
        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        try {
            content = dao.selectContent(contentId);
            if (content != null) result = content.getChapters();
            else result = Collections.emptyList();
        } finally {
            dao.cleanup();
        }
        return result;
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        Set<TextItem<Chapter>> selectedItems = selectExtension.getSelectedItems();
        int selectedCount = selectedItems.size();

        binding.actionButton.setEnabled(selectedCount > 0);
        if (0 == selectedCount) selectExtension.setSelectOnLongClick(true);
    }

    private void onCreateChaptersClick() {
        parent.readBook(content, true);
    }

    private void onActionClick() {
        List<Chapter> chapters = Stream.of(selectExtension.getSelectedItems()).map(TextItem::getTag).toList();
        if (!chapters.isEmpty()) {
            parent.splitContent(content, chapters);
            this.dismiss();
        }
    }


    // FastAdapter hooks

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        // Update  visuals
        RecyclerView.ViewHolder vh = binding.list.findViewHolderForAdapterPosition(newPosition);
        if (vh instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) vh).onDropped();
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
        void splitContent(@NonNull Content content, @NonNull List<Chapter> chapters);

        void readBook(@NonNull Content content, boolean forceShowGallery);

        void leaveSelectionMode();
    }
}
