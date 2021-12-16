package me.devsaki.hentoid.activities;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewholders.SiteItem;

/**
 * Activity to edit the left drawer where the sources are
 */
public class DrawerEditActivity extends BaseActivity implements ItemTouchCallback {

    private RecyclerView recyclerView;
    private final ItemAdapter<SiteItem> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<SiteItem> fastAdapter = FastAdapter.with(itemAdapter);

    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_drawer_edit);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            switch (clickedMenuItem.getItemId()) {
                case R.id.action_check_all:
                    onCheckAll();
                    break;
                case R.id.action_uncheck_all:
                    onUncheckAll();
                    break;
                default:
                    // Nothing to do here
            }
            return true;
        });

        // Activate drag & drop
        SimpleDragCallback dragCallback = new SimpleDragCallback(this);
        dragCallback.setNotifyAllDrops(true);
        ItemTouchHelper touchHelper = new ItemTouchHelper(dragCallback);

        // Recycler
        List<SiteItem> items = new ArrayList<>();
        List<Site> activeSites = Preferences.getActiveSites();

        // First add active sites
        for (Site s : activeSites)
            if (s.isVisible()) items.add(new SiteItem(s, true, touchHelper));
        // Then add the others
        for (Site s : Site.values())
            // We don't want to show these
            if (s.isVisible() && !activeSites.contains(s))
                items.add(new SiteItem(s, false, touchHelper));

        itemAdapter.add(items);

        recyclerView = findViewById(R.id.drawer_edit_list);
        recyclerView.setAdapter(fastAdapter);
        recyclerView.setHasFixedSize(true);

        touchHelper.attachToRecyclerView(recyclerView);

        // OK button
        View okBtn = findViewById(R.id.drawer_edit_ok_btn);
        okBtn.setOnClickListener(v -> onValidateClick());
    }

    private void onCheckAll() {
        for (SiteItem s : itemAdapter.getAdapterItems()) s.setSelected(true);
        fastAdapter.notifyDataSetChanged();
    }

    private void onUncheckAll() {
        for (SiteItem s : itemAdapter.getAdapterItems()) s.setSelected(false);
        fastAdapter.notifyDataSetChanged();
    }

    private void onValidateClick() {
        List<Site> newSites = new ArrayList<>();
        for (SiteItem s : itemAdapter.getAdapterItems())
            if (s.isSelected()) newSites.add(s.getSite());

        Preferences.setActiveSites(newSites);

        onBackPressed();
    }

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        DragDropUtil.onMove(itemAdapter, oldPosition, newPosition); // change position
        return true;
    }

    @Override
    public void itemTouchDropped(int oldPosition, int newPosition) {
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(newPosition);
        if (vh instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) vh).onDropped();
        }
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        if (viewHolder instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) viewHolder).onDragged();
        }
    }

    @Override
    public void itemTouchStopDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing
    }
}
