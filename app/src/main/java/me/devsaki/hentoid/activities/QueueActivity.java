package me.devsaki.hentoid.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import me.devsaki.hentoid.R;
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

    private TabLayout.Tab queueTab;
    private TabLayout.Tab errorsTab;

    private QueueViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_queue);

        Toolbar toolbar = findViewById(R.id.queue_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Instantiate a ViewPager and a PagerAdapter.
        TabLayout tabLayout = findViewById(R.id.queue_tabs);
        FragmentStateAdapter pagerAdapter = new ScreenSlidePagerAdapter(this);
        ViewPager2 viewPager = findViewById(R.id.queue_pager);
        viewPager.setUserInputEnabled(false);
        viewPager.setAdapter(pagerAdapter);
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (0 == position) {
                        queueTab = tab;
                        tab.setText(R.string.title_activity_queue);
                        tab.setIcon(R.drawable.ic_action_download);
                    } else {
                        errorsTab = tab;
                        tab.setText(R.string.errors);
                        tab.setIcon(R.drawable.ic_error);
                    }
                }).attach();

        ViewModelFactory vmFactory = new ViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, vmFactory).get(QueueViewModel.class);
        viewModel.getQueuePaged().observe(this, this::onQueueChanged);
        viewModel.getErrorsPaged().observe(this, this::onErrorsChanged);

        if (!Preferences.getRecentVisibility()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void onQueueChanged(PagedList<QueueRecord> result) {
        // Update queue tab
        if (result.isEmpty()) queueTab.removeBadge();
        else {
            BadgeDrawable badge = queueTab.getOrCreateBadge();
            badge.setVisible(true);
            badge.setNumber(result.size());
        }
    }

    private void onErrorsChanged(PagedList<Content> result) {
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
}
