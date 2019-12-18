package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.viewholders.SiteItem;

/**
 * Created by Robb on 10/2019
 */
public class DrawerEditActivity extends AppCompatActivity implements ItemTouchCallback {

    private final ItemAdapter<SiteItem> itemAdapter = new ItemAdapter<>();
    private final FastAdapter<SiteItem> fastAdapter = FastAdapter.with(itemAdapter);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.applyTheme(this);

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


        // Recycler
        List<SiteItem> items = new ArrayList<>();
        List<Site> activeSites = Preferences.getActiveSites();

        // First add active sites
        for (Site s : activeSites) items.add(new SiteItem(s, true));
        // Then add the others
        for (Site s : Site.values())
            // We don't want to show these
            if (s != Site.FAKKU                     // Old Fakku; kept for retrocompatibility
                    && s != Site.ASMHENTAI_COMICS   // Does not work directly
                    && s != Site.PANDA              // Dropped; kept for retrocompatibility
                    && s != Site.NONE               // Technical fallback
                    && !activeSites.contains(s)
            )
                items.add(new SiteItem(s));

        itemAdapter.add(items);

        RecyclerView recyclerView = findViewById(R.id.drawer_edit_list);
        recyclerView.setAdapter(fastAdapter);
        recyclerView.setHasFixedSize(true);

        // Activate drag & drop
        SimpleDragCallback dragCallback = new SimpleDragCallback(SimpleDragCallback.UP_DOWN);
        ItemTouchHelper touchHelper = new ItemTouchHelper(dragCallback);
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
    public void itemTouchDropped(int oldPosition, int newPosition) {
        // Nothing to do here
    }

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        DragDropUtil.INSTANCE.onMove(itemAdapter, oldPosition, newPosition); // change position
        return true;
    }
}
