package me.devsaki.hentoid.activities;

import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_ASK;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
import static me.devsaki.hentoid.util.Preferences.Constant.QUEUE_NEW_DOWNLOADS_POSITION_TOP;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.QueueActivityBundle;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.DownloadEvent;
import me.devsaki.hentoid.events.DownloadReviveEvent;
import me.devsaki.hentoid.fragments.ProgressDialogFragment;
import me.devsaki.hentoid.fragments.queue.ErrorsFragment;
import me.devsaki.hentoid.fragments.queue.QueueFragment;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.notification.NotificationManager;
import me.devsaki.hentoid.viewmodels.QueueViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.views.CloudflareWebView;
import me.devsaki.hentoid.widget.AddQueueMenu;
import timber.log.Timber;

/**
 * Handles hosting of QueueFragment for a single screen.
 */
public class QueueActivity extends BaseActivity {

    private TabLayout tabLayout;
    private TabLayout.Tab queueTab;
    private TabLayout.Tab errorsTab;

    private ViewPager2 viewPager;

    private Toolbar toolbar;
    private Toolbar selectionToolbar;
    private MenuItem searchMenu;
    private MenuItem errorStatsMenu;
    private MenuItem invertQueueMenu;
    private MenuItem cancelAllMenu;
    private MenuItem cancelAllErrorsMenu;
    private MenuItem redownloadAllMenu;

    private TextView reviveOverlay;
    private ProgressBar reviveProgress;
    private TextView reviveCancel;

    private QueueViewModel viewModel;
    private CloudflareWebView reviveWebview;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_queue);

        toolbar = findViewById(R.id.queue_toolbar);
        Helper.tryShowMenuIcons(this, toolbar.getMenu());
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        selectionToolbar = findViewById(R.id.queue_selection_toolbar);

        searchMenu = toolbar.getMenu().findItem(R.id.action_search);
        errorStatsMenu = toolbar.getMenu().findItem(R.id.action_error_stats);
        invertQueueMenu = toolbar.getMenu().findItem(R.id.action_invert_queue);
        cancelAllMenu = toolbar.getMenu().findItem(R.id.action_cancel_all);
        cancelAllErrorsMenu = toolbar.getMenu().findItem(R.id.action_cancel_all_errors);
        redownloadAllMenu = toolbar.getMenu().findItem(R.id.action_redownload_all);

        reviveOverlay = findViewById(R.id.download_revive_txt);
        reviveProgress = findViewById(R.id.download_revive_progress);
        reviveCancel = findViewById(R.id.download_revive_cancel);
        reviveCancel.setOnClickListener(v -> cancelReviveDownload());

        // Instantiate a ViewPager and a PagerAdapter.
        tabLayout = findViewById(R.id.queue_tabs);
        FragmentStateAdapter pagerAdapter = new ScreenSlidePagerAdapter(this);
        viewPager = findViewById(R.id.queue_pager);
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
        if (intent != null && intent.getExtras() != null) processIntent(intent.getExtras());

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getExtras() != null) processIntent(intent.getExtras());
    }

    private void processIntent(@NonNull Bundle extras) {
        QueueActivityBundle.Parser parser = new QueueActivityBundle.Parser(extras);
        long contentHash = parser.contentHash();
        if (contentHash != 0) {
            if (parser.isErrorsTab()) viewPager.setCurrentItem(1);
            viewModel.setContentToShowFirst(contentHash);
        }
        Site revivedSite = parser.getRevivedSite();
        String oldCookie = parser.getOldCookie();
        if (revivedSite != null && !oldCookie.isEmpty()) reviveDownload(revivedSite, oldCookie);
    }

    @Override
    protected void onDestroy() {
        if (reviveWebview != null) {
            // the WebView must be removed from the view hierarchy before calling destroy
            // to prevent a memory leak
            // See https://developer.android.com/reference/android/webkit/WebView.html#destroy%28%29
            ((ViewGroup) reviveWebview.getParent()).removeView(reviveWebview);
            reviveWebview.removeAllViews();
            reviveWebview.destroy();
            reviveWebview = null;
        }

        compositeDisposable.clear();

        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void onTabSelected(int position) {
        // Update permanent toolbar
        searchMenu.setVisible(0 == position);
        invertQueueMenu.setVisible(0 == position);
        cancelAllMenu.setVisible(0 == position);
        cancelAllErrorsMenu.setVisible(1 == position);
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
        Helper.tryShowMenuIcons(this, selectionToolbar.getMenu());

        // Log the view of the tab
        Bundle bundle = new Bundle();
        bundle.putInt("tag", position);
        FirebaseAnalytics.getInstance(this).logEvent("view_queue_tab", bundle);
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

    /**
     * Redownload the given list of Content according to the given parameters
     * NB : Used by both the regular redownload and redownload from scratch
     *
     * @param contentList    List of content to be redownloaded
     * @param reparseContent True if the content (general metadata) has to be re-parsed from the site; false to keep
     * @param reparseImages  True if the images have to be re-detected and redownloaded from the site; false to keep
     */
    public void redownloadContent(@NonNull final List<Content> contentList, boolean reparseContent, boolean reparseImages) {
        if (Preferences.getQueueNewDownloadPosition() == QUEUE_NEW_DOWNLOADS_POSITION_ASK) {
            AddQueueMenu.show(this, tabLayout, this, (position, item) ->
                    redownloadContent(contentList, reparseContent, reparseImages, (0 == position) ? QUEUE_NEW_DOWNLOADS_POSITION_TOP : QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM)
            );
        } else
            redownloadContent(contentList, reparseContent, reparseImages, Preferences.getQueueNewDownloadPosition());
    }

    /**
     * Redownload the given list of Content according to the given parameters
     * NB : Used by both the regular redownload and redownload from scratch
     *
     * @param contentList    List of content to be redownloaded
     * @param reparseContent True if the content (general metadata) has to be re-parsed from the site; false to keep
     * @param reparseImages  True if the images have to be re-detected and redownloaded from the site; false to keep
     * @param position       Position of the new item to redownload, either QUEUE_NEW_DOWNLOADS_POSITION_TOP or QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM
     */
    private void redownloadContent(@NonNull final List<Content> contentList, boolean reparseContent, boolean reparseImages, int position) {
        if (reparseContent || reparseImages)
            ProgressDialogFragment.invoke(getSupportFragmentManager(), getResources().getString(R.string.redownload_queue_progress), R.plurals.book);
        viewModel.redownloadContent(contentList, reparseContent, reparseImages, position,
                nbSuccess -> {
                    String message = getResources().getQuantityString(R.plurals.redownloaded_scratch, nbSuccess, nbSuccess, contentList.size());
                    Snackbar snackbar = Snackbar.make(tabLayout, message, BaseTransientBottomBar.LENGTH_LONG);
                    snackbar.show();
                },
                Timber::i);
    }

    private void changeReviveUIVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        reviveOverlay.setVisibility(visibility);
        reviveProgress.setVisibility(visibility);
        reviveCancel.setVisibility(visibility);
    }

    /**
     * Revive event handler
     *
     * @param event Broadcasted event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReviveDownload(DownloadReviveEvent event) {
        if (reviveWebview != null) return;

        reviveDownload(event.site, event.url);
    }

    // TODO doc
    private void reviveDownload(@NonNull final Site revivedSite, @NonNull final String oldCookie) {
        Timber.d(">> REVIVAL ASKED @ %s", revivedSite.getUrl());

        // Remove any notification
        NotificationManager userActionNotificationManager = new NotificationManager(this, R.id.user_action_notification);
        userActionNotificationManager.cancel();

        // Nuke the cookie to force its refresh
        String domain = "." + HttpHelper.getDomainFromUri(revivedSite.getUrl());
        HttpHelper.setCookies(domain, Consts.CLOUDFLARE_COOKIE + "=;Max-Age=0; secure; HttpOnly");

        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content).getRootView();

        try {
            reviveWebview = new CloudflareWebView(this, revivedSite);
        } catch (Resources.NotFoundException e) {
            // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
            // Creating with the application Context fixes this, but is not generally recommended for view creation
            reviveWebview = new CloudflareWebView(Helper.getFixedContext(this), revivedSite);
        }
        // TODO no need to add it to the layout
        reviveWebview.setVisibility(View.GONE);
        rootView.addView(reviveWebview);
        reviveWebview.loadUrl(revivedSite.getUrl());

        reviveProgress.setMax((int) Math.round(90 / 1.5)); // How many ticks in 1.5 minutes, which is the maximum time for revival
        reviveProgress.setProgress(reviveProgress.getMax());
        changeReviveUIVisibility(true);

        AtomicInteger reloadCounter = new AtomicInteger(0);
        // Wait for cookies to refresh
        compositeDisposable.add(Observable.timer(1500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .repeat()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(v -> {
                    final String cfcookie = HttpHelper.parseCookies(HttpHelper.getCookies(revivedSite.getUrl())).get(Consts.CLOUDFLARE_COOKIE);
                    if (cfcookie != null && !cfcookie.isEmpty() && !cfcookie.equals(oldCookie)) {
                        Timber.d("CF-COOKIE : refreshed !");
                        EventBus.getDefault().post(new DownloadEvent(DownloadEvent.Type.EV_UNPAUSE));
                        cancelReviveDownload();
                    } else {
                        Timber.v("CF-COOKIE : not refreshed");
                        int currentProgress = reviveProgress.getProgress();
                        if (currentProgress > 0) reviveProgress.setProgress(currentProgress - 1);
                        // Reload if nothing for 7.5s
                        if (reloadCounter.incrementAndGet() > 5) {
                            reloadCounter.set(0);
                            Timber.v("CF-COOKIE : RELOAD");
                            reviveWebview.reload();
                        }
                    }
                })
        );
    }

    private void cancelReviveDownload() {
        compositeDisposable.clear();

        changeReviveUIVisibility(false);

        ((ViewGroup) reviveWebview.getParent()).removeView(reviveWebview);
        reviveWebview.removeAllViews();
        reviveWebview.destroy();
        reviveWebview = null;
    }
}
