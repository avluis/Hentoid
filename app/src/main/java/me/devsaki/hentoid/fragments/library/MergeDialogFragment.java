package me.devsaki.hentoid.fragments.library;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.databinding.DialogLibraryMergeBinding;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewholders.TextItem;

public final class MergeDialogFragment extends DialogFragment implements ItemTouchCallback {

    private static final String KEY_CONTENTS = "contents";
    private static final String KEY_DELETE_DEFAULT = "delete_default";

    // === UI
    private DialogLibraryMergeBinding binding = null;
    private EditText newTitleTxt;

    private final ItemAdapter<TextItem<Content>> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<TextItem<Content>> fastAdapter = FastAdapter.with(itemAdapter);
    private ItemTouchHelper touchHelper;

    // === VARIABLES
    private Parent parent;
    private long[] contentIds;
    private boolean deleteDefault;
    private String initialTitle = "";


    public static void invoke(
            @NonNull final Fragment parent,
            @NonNull final List<Content> contentList,
            boolean deleteDefault) {
        MergeDialogFragment fragment = new MergeDialogFragment();

        Bundle args = new Bundle();
        args.putLongArray(KEY_CONTENTS, Helper.getPrimitiveArrayFromList(Stream.of(contentList).map(Content::getId).toList()));
        args.putBoolean(KEY_DELETE_DEFAULT, deleteDefault);
        fragment.setArguments(args);

        fragment.show(parent.getChildFragmentManager(), null);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getArguments()) throw new IllegalArgumentException("No arguments found");
        contentIds = getArguments().getLongArray(KEY_CONTENTS);
        if (null == contentIds || 0 == contentIds.length)
            throw new IllegalArgumentException("No content IDs");
        deleteDefault = getArguments().getBoolean(KEY_DELETE_DEFAULT, false);

        parent = (Parent) getParentFragment();
    }

    @Override
    public void onDestroy() {
        parent = null;
        super.onDestroy();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        parent.leaveSelectionMode();
        super.onCancel(dialog);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        binding = DialogLibraryMergeBinding.inflate(inflater, container, false);
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

        List<Content> contentList = loadContentList();
        if (contentList.isEmpty()) return;

        itemAdapter.set(Stream.of(contentList).map(s -> new TextItem<>(s.getTitle(), s, true, false, false, touchHelper)).toList());

        // Activate drag & drop
        SimpleDragCallback dragCallback = new SimpleDragCallback(this);
        dragCallback.setNotifyAllDrops(true);
        touchHelper = new ItemTouchHelper(dragCallback);
        touchHelper.attachToRecyclerView(binding.list);
        fastAdapter.addEventHook(
                new TextItem.DragHandlerTouchEvent<>(
                        position -> {
                            RecyclerView.ViewHolder vh = binding.list.findViewHolderForAdapterPosition(position);
                            if (vh != null) touchHelper.startDrag(vh);
                        }
                )
        );

        binding.list.setAdapter(fastAdapter);

        initialTitle = contentList.get(0).getTitle();
        newTitleTxt = binding.titleNew.getEditText();
        if (newTitleTxt != null) newTitleTxt.setText(initialTitle);

        binding.mergeDeleteSwitch.setChecked(deleteDefault);

        binding.actionButton.setOnClickListener(v -> onActionClick());
    }

    private List<Content> loadContentList() {
        List<Content> result;
        CollectionDAO dao = new ObjectBoxDAO(requireContext());
        try {
            result = dao.selectContent(contentIds);
        } finally {
            dao.cleanup();
        }
        return result;
    }

    private void onActionClick() {
        List<Content> contents = Stream.of(itemAdapter.getAdapterItems()).map(TextItem::getTag).toList();
        String newTitleStr = (null == newTitleTxt) ? "" : newTitleTxt.getText().toString();
        parent.mergeContents(contents, newTitleStr, binding.mergeDeleteSwitch.isChecked());
        this.dismissAllowingStateLoss();
    }


    // FastAdapter hooks

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        // Update  visuals
        RecyclerView.ViewHolder vh = binding.list.findViewHolderForAdapterPosition(newPosition);
        if (vh instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) vh).onDropped();
        }
        // Update new title if unedited
        if (0 == newPosition && newTitleTxt.getText().toString().equals(initialTitle)) {
            initialTitle = itemAdapter.getAdapterItem(0).getText();
            newTitleTxt.setText(initialTitle);
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
        void mergeContents(@NonNull List<Content> contentList, @NonNull String newTitle, boolean deleteAfterMerging);

        void leaveSelectionMode();
    }
}
