package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.SiteFlex;

/**
 * Created by Robb on 10/2019
 */
public class DrawerEditActivity extends AppCompatActivity {

    private FlexibleAdapter<SiteFlex> siteAdapter;

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


        // Recycler
        List<SiteFlex> items = new ArrayList<>();
        List<Site> activeSites = Preferences.getActiveSites();

        // First add active sites
        for (Site s : activeSites) items.add(new SiteFlex(s, true));
        // Then add the others
        for (Site s : Site.values())
            // We don't want to show these
            if (s != Site.FAKKU                     // Old Fakku; kept for retrocompatibility
                    && s != Site.ASMHENTAI_COMICS   // Does not work directly
                    && s != Site.PANDA              // Dropped; kept for retrocompatibility
                    && s != Site.NONE               // Technical fallback
                    && !activeSites.contains(s)
            )
                items.add(new SiteFlex(s));

        siteAdapter = new FlexibleAdapter<>(items, null, true);

        RecyclerView recyclerView = findViewById(R.id.drawer_edit_list);
        recyclerView.setAdapter(siteAdapter);
        recyclerView.setHasFixedSize(true);
        siteAdapter.setHandleDragEnabled(true);

        // OK button
        View okBtn = findViewById(R.id.drawer_edit_ok_btn);
        okBtn.setOnClickListener(v -> onValidateClick());
    }

    private void onCheckAll() {
        for (SiteFlex s : siteAdapter.getCurrentItems()) s.setSelected(true);
        siteAdapter.notifyDataSetChanged();
    }

    private void onUncheckAll() {
        for (SiteFlex s : siteAdapter.getCurrentItems()) s.setSelected(false);
        siteAdapter.notifyDataSetChanged();
    }

    private void onValidateClick() {
        List<Site> newSites = new ArrayList<>();
        for (SiteFlex s : siteAdapter.getCurrentItems())
            if (s.isSelected()) newSites.add(s.getSite());

        Preferences.setActiveSites(newSites);

        onBackPressed();
    }
}
