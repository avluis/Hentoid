package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
//import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.fragments.queue.ErrorsFragment;
import me.devsaki.hentoid.fragments.queue.QueueFragment;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

/**
 * Handles hosting of QueueFragment for a single screen.
 */
public class QueueActivity extends BaseActivity {

    private TabLayout tabLayout;
    private TabLayout.Tab queueTab;
    private TabLayout.Tab errorsTab;

    private Toolbar toolbar;
    private Toolbar selectionToolbar;
    private MenuItem errorStatsMenu;
    private MenuItem invertQueueMenu;
    private MenuItem cancelAllMenu;
    private MenuItem redownloadAllMenu;

    private QueueViewModel viewModel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_queue);

        toolbar = findViewById(R.id.queue_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        selectionToolbar = findViewById(R.id.queue_selection_toolbar);

        errorStatsMenu = toolbar.getMenu().findItem(R.id.action_error_stats);
        invertQueueMenu = toolbar.getMenu().findItem(R.id.action_invert_queue);
        cancelAllMenu = toolbar.getMenu().findItem(R.id.action_cancel_all);
        redownloadAllMenu = toolbar.getMenu().findItem(R.id.action_redownload_all);

        // Instantiate a ViewPager and a PagerAdapter.
        tabLayout = findViewById(R.id.queue_tabs);
        FragmentStateAdapter pagerAdapter = new ScreenSlidePagerAdapter(this);
        ViewPager2 viewPager = findViewById(R.id.queue_pager);
        viewPager.setUserInputEnabled(false); // Disable swipe to change tabs
        viewPager.setAdapter(pagerAdapter);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (0 == position) {
                        queueTab = tab;
                        tab.setText(R.string.queue_queue_tab);
                        tab.setIcon(R.drawable.ic_action_download);
                    } else {
                        errorsTab = tab;
                        tab.setText(R.string.queue_errors_tab);
                        tab.setIcon(R.drawable.ic_error);
                    }
                }).attach();
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                onTabSelected(position);
            }
        });

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(QueueViewModel.class);
        viewModel.getQueue().observe(this, this::onQueueChanged);
        viewModel.getErrors().observe(this, this::onErrorsChanged);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            QueueActivityBundle.Parser parser = new QueueActivityBundle.Parser(intent.getExtras());
            long contentHash = parser.contentHash();
            if (contentHash != 0) {
                if (parser.isErrorsTab()) viewPager.setCurrentItem(1);
                viewModel.setContentToShowFirst(contentHash);
            }
        }
    }

    private void onTabSelected(int position) {
        // Update permanent toolbar
        invertQueueMenu.setVisible(0 == position);
        cancelAllMenu.setVisible(true);
        redownloadAllMenu.setVisible(1 == position);
        if (1 == position)
            errorStatsMenu.setVisible(false); // That doesn't mean it should be visible at all times on tab 0 !

        // Update selection toolbar
        selectionToolbar.setVisibility(View.GONE);
        selectionToolbar.getMenu().clear();
        if (0 == position)
            selectionToolbar.inflateMenu(R.menu.queue_queue_selection_menu);
        else
            selectionToolbar.inflateMenu(R.menu.queue_error_selection_menu);

        // Log the view of the tab
        Bundle bundle = new Bundle();
        bundle.putInt("tag", position);
        //FirebaseAnalytics.getInstance(this).logEvent("view_queue_tab", bundle);
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public Toolbar getSelectionToolbar() {
        return selectionToolbar;
    }

    private void onQueueChanged(List<QueueRecord> result) {
        // Update queue tab
        if (result.isEmpty()) queueTab.removeBadge();
        else {
            BadgeDrawable badge = queueTab.getOrCreateBadge();
            badge.setVisible(true);
            badge.setNumber(result.size());
        }
    }

    private void onErrorsChanged(List<Content> result) {
        // Update errors tab
        if (result.isEmpty()) errorsTab.removeBadge();
        else {
            BadgeDrawable badge = errorsTab.getOrCreateBadge();
            badge.setVisible(true);
            badge.setNumber(result.size());
        }
    }

    private static class ScreenSlidePagerAdapter extends FragmentStateAdapter {
        ScreenSlidePagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (0 == position) return new QueueFragment();
            else return new ErrorsFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public void redownloadContent(@NonNull final List<Content> contentList, boolean reparseContent, boolean reparseImages) {
        viewModel.redownloadContent(contentList, reparseContent, reparseImages,
                () -> {
                    String message = getResources().getQuantityString(R.plurals.redownloaded_scratch, contentList.size(), contentList.size());
                    Snackbar snackbar = Snackbar.make(tabLayout, message, BaseTransientBottomBar.LENGTH_LONG);
                    snackbar.show();
                });
    }

    // TODO deselect on back button
}
