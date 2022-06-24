package me.devsaki.hentoid.fragments.library;

import static me.devsaki.hentoid.events.CommunicationEvent.EV_CLOSED;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_DRAWER;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.activities.AboutActivity;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.ToolsActivity;
import me.devsaki.hentoid.databinding.FragmentNavigationDrawerBinding;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.events.UpdateEvent;
import me.devsaki.hentoid.json.core.UpdateInfo;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.viewholders.DrawerItem;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;

public final class NavigationDrawerFragment extends Fragment {

    private LibraryActivity parentActivity;

    private LibraryViewModel viewModel;
    private UpdateEvent updateInfo;

    // Settings listener
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> onSharedPreferenceChanged(k);

    private FragmentNavigationDrawerBinding binding = null;
    private final ItemAdapter<DrawerItem> drawerAdapter = new ItemAdapter<>();
    private final FastAdapter<DrawerItem> fastAdapter = FastAdapter.with(drawerAdapter);


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        parentActivity = (LibraryActivity) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        Preferences.unregisterPrefsChangedListener(prefsListener);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNavigationDrawerBinding.inflate(inflater, container, false);

        binding.drawerAboutBtn.setOnClickListener(v -> onAboutClick());
        binding.drawerAppPrefsBtn.setOnClickListener(v -> onPrefsClick());
        binding.drawerToolsBtn.setOnClickListener(v -> onToolsClick());
        binding.drawerAppQueueBtn.setOnClickListener(v -> onQueueClick());

        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(p));
        binding.drawerList.setAdapter(fastAdapter);

        updateItems();

        Preferences.registerPrefsChangedListener(prefsListener);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel.getTotalQueue().observe(getViewLifecycleOwner(), this::onTotalQueueChanged);
    }

    private void updateItems() {
        List<DrawerItem> drawerItems = new ArrayList<>();

        List<Site> activeSites = Preferences.getActiveSites();
        for (Site s : activeSites)
            if (s.isVisible()) drawerItems.add(new DrawerItem(s));

        drawerAdapter.clear();
        drawerAdapter.add(0, drawerItems);
        applyFlagsAndAlerts();
    }

    private boolean onItemClick(int position) {
        DrawerItem item = drawerAdapter.getAdapterItem(position);
        launchActivity(item.getActivityClass());
        return true;
    }

    private void launchActivity(@NonNull Class<?> activityClass) {
        Intent intent = new Intent(parentActivity, activityClass);
        ContextCompat.startActivity(parentActivity, intent, null);

        parentActivity.overridePendingTransition(0, 0);
        parentActivity.closeNavigationDrawer();
    }

    private void showFlagAboutItem() {
        binding.drawerAboutBtnBadge.setVisibility(View.VISIBLE);
    }

    private void onTotalQueueChanged(int totalQueue) {
        if (totalQueue > 0) {
            String text = (totalQueue > 99) ? "99+" : Integer.toString(totalQueue);
            if (1 == text.length()) text = " " + text + " ";
            binding.drawerQueueBtnBadge.setText(text);
            binding.drawerQueueBtnBadge.setVisibility(View.VISIBLE);
        } else {
            binding.drawerQueueBtnBadge.setVisibility(View.GONE);
        }
    }

    private void showFlagAlerts(Map<Site, UpdateInfo.SourceAlert> alerts) {
        List<DrawerItem> menuItems = drawerAdapter.getAdapterItems();
        int index = 0;
        for (DrawerItem menuItem : menuItems) {
            if (menuItem.getSite() != null && alerts.containsKey(menuItem.getSite())) {
                UpdateInfo.SourceAlert alert = alerts.get(menuItem.getSite());
                if (alert != null) {
                    menuItem.setAlertStatus(alert.getStatus());
                    fastAdapter.notifyItemChanged(index);
                }
            }
            index++;
        }
    }

    private void applyFlagsAndAlerts() {
        if (null == updateInfo) return;

        // Display the "new update available" flag
        if (updateInfo.hasNewVersion) showFlagAboutItem();

        // Display the site alert flags, if any
        if (!updateInfo.sourceAlerts.isEmpty()) showFlagAlerts(updateInfo.sourceAlerts);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onUpdateEvent(UpdateEvent event) {
        updateInfo = event;
        applyFlagsAndAlerts();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDrawerClosed(CommunicationEvent event) {
        if (event.getRecipient() != RC_DRAWER) return;
        if (EV_CLOSED == event.getType()) binding.drawerList.scrollToPosition(0);
    }

    private void onAboutClick() {
        launchActivity(AboutActivity.class);
    }

    private void onPrefsClick() {
        launchActivity(PrefsActivity.class);
    }

    private void onToolsClick() {
        launchActivity(ToolsActivity.class);
    }

    private void onQueueClick() {
        launchActivity(QueueActivity.class);
    }

    /**
     * Callback for any change in Preferences
     */
    private void onSharedPreferenceChanged(String key) {
        if (Preferences.Key.ACTIVE_SITES.equals(key)) updateItems();
    }
}
