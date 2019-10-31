package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.viewholders.SiteFlex;

/**
 * Created by Robb on 10/2019
 */
public class DrawerEditActivity extends BaseActivity {

    private FlexibleAdapter<SiteFlex> siteAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_drawer_edit);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.drawer_edit_menu);

        toolbar.setNavigationIcon(R.drawable.ic_action_clear);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            switch (clickedMenuItem.getItemId()) {
                case R.id.action_check_all:
                    onCheckAll();
                    break;
                case R.id.action_uncheck_all:
                    onUncheckAll();
                    break;
            }
            return true;
        });


        // Recycler
        List<SiteFlex> items = new ArrayList<>();
        for (Site s : Site.values())
            // We don't want to show these
            if (s != Site.FAKKU                     // Old Fakku; kept for retrocompatibility
                    && s != Site.ASMHENTAI_COMICS   // Does not work directly
                    && s != Site.PANDA              // Dropped; kept for retrocompatibility
                    && s != Site.NONE)              // Technical fallback)
                items.add(new SiteFlex(s));

        siteAdapter = new FlexibleAdapter<>(items, null, true);

        RecyclerView recyclerView = findViewById(R.id.drawer_edit_list);
        recyclerView.setAdapter(siteAdapter);
        recyclerView.setHasFixedSize(true);
        siteAdapter.setHandleDragEnabled(true);

        // OK button
        View okBtn = findViewById(R.id.drawer_edit_ok_btn);
        okBtn.setOnClickListener(this::onValidateClick);
    }

    private void onCheckAll() {

    }

    private void onUncheckAll() {

    }

    private void onValidateClick(View item) {
        // TODO - record selected sites and their order
        onBackPressed();
    }
}
