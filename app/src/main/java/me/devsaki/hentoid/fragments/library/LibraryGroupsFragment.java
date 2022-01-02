package me.devsaki.hentoid.fragments.library;

import static androidx.core.view.ViewCompat.requireViewById;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_DISABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_ENABLE;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_SEARCH;
import static me.devsaki.hentoid.events.CommunicationEvent.EV_UPDATE_SORT;
import static me.devsaki.hentoid.events.CommunicationEvent.RC_GROUPS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.diff.DiffCallback;
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil;
import com.mikepenz.fastadapter.drag.ItemTouchCallback;
import com.mikepenz.fastadapter.drag.SimpleDragCallback;
import com.mikepenz.fastadapter.extensions.ExtensionsFactories;
import com.mikepenz.fastadapter.listeners.ClickEventHook;
import com.mikepenz.fastadapter.select.SelectExtension;
import com.mikepenz.fastadapter.select.SelectExtensionFactory;
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback;
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback;
import com.mikepenz.fastadapter.utils.DragDropUtil;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.LibraryActivity;
import me.devsaki.hentoid.activities.PrefsActivity;
import me.devsaki.hentoid.activities.bundles.GroupItemBundle;
import me.devsaki.hentoid.activities.bundles.PrefsActivityBundle;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.events.CommunicationEvent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.viewholders.GroupDisplayItem;
import me.devsaki.hentoid.viewholders.IDraggableViewHolder;
import me.devsaki.hentoid.viewmodels.LibraryViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.AutofitGridLayoutManager;
import me.devsaki.hentoid.widget.FastAdapterPreClickSelectHelper;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;
import timber.log.Timber;

@SuppressLint("NonConstantResourceId")
public class LibraryGroupsFragment extends Fragment implements ItemTouchCallback, SimpleSwipeCallback.ItemSwipeCallback {

    // ======== COMMUNICATION
    private OnBackPressedCallback callback;
    // Viewmodel
    private LibraryViewModel viewModel;
    // Activity
    private WeakReference<LibraryActivity> activity;


    // ======== UI
    // Text that displays in the background when the list is empty
    private TextView emptyText;
    // Main view where books are displayed
    private RecyclerView recyclerView;
    // LayoutManager of the recyclerView
    private LinearLayoutManager llm;

    // === SORT TOOLBAR
    // Sort direction button
    private ImageView sortDirectionButton;
    // Sort field button
    private TextView sortFieldButton;

    // === FASTADAPTER COMPONENTS AND HELPERS
    private ItemAdapter<GroupDisplayItem> itemAdapter;
    private FastAdapter<GroupDisplayItem> fastAdapter;
    private SelectExtension<GroupDisplayItem> selectExtension;
    private ItemTouchHelper touchHelper;


    // ======== VARIABLES
    // Records the system time (ms) when back button has been last pressed (to detect "double back button" event)
    private long backButtonPressed;
    // Total number of books in the whole unfiltered library
    private int totalContentCount;
    // TODO doc
    private boolean firstLibraryLoad = true;
    // TODO doc
    private boolean enabled = true;


    public static final DiffCallback<GroupDisplayItem> GROUPITEM_DIFF_CALLBACK = new DiffCallback<GroupDisplayItem>() {
        @Override
        public boolean areItemsTheSame(GroupDisplayItem oldItem, GroupDisplayItem newItem) {
            return oldItem.getIdentifier() == newItem.getIdentifier();
        }

        @Override
        public boolean areContentsTheSame(GroupDisplayItem oldItem, GroupDisplayItem newItem) {
            return oldItem.getGroup().picture.getTargetId() == newItem.getGroup().picture.getTargetId()
                    && oldItem.getGroup().isFavourite() == newItem.getGroup().isFavourite()
                    && oldItem.getGroup().items.size() == newItem.getGroup().items.size();
        }

        @Override
        public @org.jetbrains.annotations.Nullable Object getChangePayload(GroupDisplayItem oldItem, int oldPos, GroupDisplayItem newItem, int newPos) {
            GroupItemBundle.Builder diffBundleBuilder = new GroupItemBundle.Builder();

            if (!newItem.getGroup().picture.isNull() && oldItem.getGroup().picture.getTargetId() != newItem.getGroup().picture.getTargetId()) {
                diffBundleBuilder.setCoverUri(newItem.getGroup().picture.getTarget().getUsableUri());
            }
            if (oldItem.getGroup().isFavourite() != newItem.getGroup().isFavourite()) {
                diffBundleBuilder.setFavourite(newItem.getGroup().isFavourite());
            }

            if (diffBundleBuilder.isEmpty()) return null;
            else return diffBundleBuilder.getBundle();
        }
    };


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(requireActivity() instanceof LibraryActivity))
            throw new IllegalStateException("Parent activity has to be a LibraryActivity");
        activity = new WeakReference<>((LibraryActivity) requireActivity());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ExtensionsFactories.INSTANCE.register(new SelectExtensionFactory());
        EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_library_groups, container, false);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(LibraryViewModel.class);

        initUI(rootView);
        activity.get().initFragmentToolbars(selectExtension, this::onToolbarItemClicked, this::onSelectionToolbarItemClicked);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firstLibraryLoad = true;
        viewModel.getGroups().observe(getViewLifecycleOwner(), this::onGroupsChanged);
        viewModel.getTotalGroup().observe(getViewLifecycleOwner(), this::onTotalGroupsChanged);
        viewModel.getLibraryPaged().observe(getViewLifecycleOwner(), this::onLibraryChanged);

        // Trigger a blank search
        // TODO when group is reached from FLAT through the "group by" menu, this triggers a double-load and a screen blink
        viewModel.setGrouping(
                Preferences.getGroupingDisplay(),
                Preferences.getGroupSortField(),
                Preferences.isGroupSortDesc(),
                Preferences.getArtistGroupVisibility(),
                activity.get().isGroupFavsChecked());
    }

    public void onEnable() {
        enabled = true;
        if (callback != null) callback.setEnabled(true);
    }

    public void onDisable() {
        enabled = false;
        if (callback != null) callback.setEnabled(false);
    }

    /**
     * Initialize the UI components
     *
     * @param rootView Root view of the library screen
     */
    private void initUI(@NonNull View rootView) {
        emptyText = requireViewById(rootView, R.id.library_empty_txt);
        sortDirectionButton = activity.get().getSortDirectionButton();
        sortFieldButton = activity.get().getSortFieldButton();

        // RecyclerView
        recyclerView = requireViewById(rootView, R.id.library_list);
        if (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay())
            llm = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        else
            llm = new AutofitGridLayoutManager(requireContext(), (int) getResources().getDimension(R.dimen.card_grid_width));
        recyclerView.setLayoutManager(llm);
        new FastScrollerBuilder(recyclerView).build();

        // Pager
        setPagingMethod();

        updateSortControls();
        addCustomBackControl();
    }

    private void addCustomBackControl() {
        if (callback != null) callback.remove();
        callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                customBackPress();
            }
        };
        activity.get().getOnBackPressedDispatcher().addCallback(activity.get(), callback);
    }

    private void updateSortControls() {
        // Sort controls
        sortDirectionButton.setImageResource(Preferences.isGroupSortDesc() ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
        sortDirectionButton.setOnClickListener(v -> {
            boolean sortDesc = !Preferences.isGroupSortDesc();
            Preferences.setGroupSortDesc(sortDesc);
            // Update icon
            sortDirectionButton.setImageResource(sortDesc ? R.drawable.ic_simple_arrow_down : R.drawable.ic_simple_arrow_up);
            // Run a new search
            viewModel.searchGroup(Preferences.getGroupingDisplay(), activity.get().getQuery(), Preferences.getGroupSortField(), sortDesc, Preferences.getArtistGroupVisibility(), activity.get().isGroupFavsChecked());
            activity.get().sortCommandsAutoHide(true, null);
        });
        sortFieldButton.setText(getNameFromFieldCode(Preferences.getGroupSortField()));
        sortFieldButton.setOnClickListener(v -> {
            // Load and display the field popup menu
            PopupMenu popup = new PopupMenu(requireContext(), sortDirectionButton);
            popup.getMenuInflater()
                    .inflate(R.menu.library_groups_sort_popup, popup.getMenu());
            popup.getMenu().findItem(R.id.sort_custom).setVisible(Preferences.getGroupingDisplay().canReorderGroups());
            popup.setOnMenuItemClickListener(item -> {
                // Update button text
                sortFieldButton.setText(item.getTitle());
                item.setChecked(true);
                int fieldCode = getFieldCodeFromMenuId(item.getItemId());
                Preferences.setGroupSortField(fieldCode);
                // Run a new search
                viewModel.searchGroup(Preferences.getGroupingDisplay(), activity.get().getQuery(), fieldCode, Preferences.isGroupSortDesc(), Preferences.getArtistGroupVisibility(), activity.get().isGroupFavsChecked());
                activity.get().sortCommandsAutoHide(true, popup);
                return true;
            });
            popup.show(); //showing popup menu
            activity.get().sortCommandsAutoHide(true, popup);
        }); //closing the setOnClickListener method
    }

    private int getFieldCodeFromMenuId(@IdRes int menuId) {
        switch (menuId) {
            case (R.id.sort_title):
                return Preferences.Constant.ORDER_FIELD_TITLE;
            case (R.id.sort_books):
                return Preferences.Constant.ORDER_FIELD_CHILDREN;
            case (R.id.sort_dl_date):
                return Preferences.Constant.ORDER_FIELD_DOWNLOAD_DATE;
            case (R.id.sort_custom):
                return Preferences.Constant.ORDER_FIELD_CUSTOM;
            default:
                return Preferences.Constant.ORDER_FIELD_NONE;
        }
    }

    private int getNameFromFieldCode(int prefFieldCode) {
        switch (prefFieldCode) {
            case (Preferences.Constant.ORDER_FIELD_TITLE):
                return R.string.sort_title;
            case (Preferences.Constant.ORDER_FIELD_CHILDREN):
                return R.string.sort_books;
            case (Preferences.Constant.ORDER_FIELD_DOWNLOAD_DATE):
                return R.string.sort_dl_date;
            case (Preferences.Constant.ORDER_FIELD_CUSTOM):
                return R.string.sort_custom;
            default:
                return R.string.sort_invalid;
        }
    }

    private boolean onToolbarItemClicked(@NonNull MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_edit:
                toggleEditMode();
                break;
            case R.id.action_edit_cancel:
                cancelEditMode();
                break;
            case R.id.action_group_new:
                newGroupPrompt();
                break;
            default:
                return activity.get().toolbarOnItemClicked(menuItem);
        }
        return true;
    }

    private boolean onSelectionToolbarItemClicked(@NonNull MenuItem menuItem) {
        boolean keepToolbar = false;
        switch (menuItem.getItemId()) {
            case R.id.action_edit_name:
                editSelectedItemName();
                break;
            case R.id.action_delete:
                deleteSelectedItems();
                break;
            case R.id.action_archive:
                archiveSelectedItems();
                break;
            case R.id.action_select_all:
                // Make certain _everything_ is properly selected (selectExtension.select() as doesn't get everything the 1st time it's called)
                int count = 0;
                while (selectExtension.getSelections().size() < itemAdapter.getAdapterItemCount() && ++count < 5)
                    selectExtension.select(Stream.range(0, itemAdapter.getAdapterItemCount()).toList());
                keepToolbar = true;
                break;
            default:
                activity.get().getSelectionToolbar().setVisibility(View.GONE);
                return false;
        }
        if (!keepToolbar) activity.get().getSelectionToolbar().setVisibility(View.GONE);
        return true;
    }

    private void toggleEditMode() {
        activity.get().toggleEditMode();

        // Leave edit mode by validating => Save new item position
        if (!activity.get().isEditMode()) {
            // Set ordering field to custom
            Preferences.setGroupSortField(Preferences.Constant.ORDER_FIELD_CUSTOM);
            sortFieldButton.setText(getNameFromFieldCode(Preferences.Constant.ORDER_FIELD_CUSTOM));
            // Set ordering direction to ASC (we just manually ordered stuff; it has to be displayed as is)
            Preferences.setGroupSortDesc(false);
            viewModel.saveGroupPositions(Stream.of(itemAdapter.getAdapterItems()).map(GroupDisplayItem::getGroup).withoutNulls().toList());
        }

        setPagingMethod();
    }

    private void cancelEditMode() {
        activity.get().setEditMode(false);
        setPagingMethod();
    }

    private void newGroupPrompt() {
        InputDialog.invokeInputDialog(requireActivity(), R.string.new_group_name, groupName -> viewModel.newGroup(Preferences.getGroupingDisplay(), groupName, this::onNewGroupNameExists));
    }

    private void onNewGroupNameExists() {
        ToastHelper.toast(R.string.group_name_exists);
        newGroupPrompt();
    }

    /**
     * Callback for the "favourite" button of the group holder
     *
     * @param group Group whose "favourite" button has been clicked on
     */
    private void onGroupFavouriteClick(@NonNull Group group) {
        viewModel.toggleGroupFavourite(group);
    }

    /**
     * Callback for the "delete item" action button
     */
    private void deleteSelectedItems() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        if (!selectedItems.isEmpty()) {
            List<Group> selectedGroups = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().toList();
            List<List<Content>> selectedContentLists = Stream.of(selectedGroups).map(g -> viewModel.getGroupContents(g)).toList();
            List<Content> selectedContent = new ArrayList<>();
            for (List<Content> list : selectedContentLists) selectedContent.addAll(list);

            // Remove external items if they can't be deleted
            if (!Preferences.isDeleteExternalLibrary()) {
                List<Content> contentToDelete = Stream.of(selectedContent).filterNot(c -> c.getStatus().equals(StatusContent.EXTERNAL)).toList();
                int diff = selectedContent.size() - contentToDelete.size();
                // Remove undeletable books from the list
                if (diff > 0) {
                    Snackbar.make(recyclerView, getResources().getQuantityString(R.plurals.external_not_removed, diff, diff), BaseTransientBottomBar.LENGTH_LONG).show();
                    selectedContent = contentToDelete;
                    // Rebuild the groups list from the remaining contents if needed
                    if (Preferences.getGroupingDisplay().canDeleteGroups())
                        selectedGroups = Stream.of(selectedContent).flatMap(c -> Stream.of(c.groupItems)).map(gi -> gi.group.getTarget()).toList();
                }
            }
            // Don't remove non-deletable groups
            if (!Preferences.getGroupingDisplay().canDeleteGroups()) selectedGroups.clear();

            if (!selectedContent.isEmpty() || !selectedGroups.isEmpty()) {
                PowerMenu.Builder powerMenuBuilder = new PowerMenu.Builder(requireContext())
                        .setOnDismissListener(() -> selectExtension.deselect(selectExtension.getSelections()))
                        .setWidth(getResources().getDimensionPixelSize(R.dimen.dialog_width))
                        .setAnimation(MenuAnimation.SHOW_UP_CENTER)
                        .setMenuRadius(10f)
                        .setIsMaterial(true)
                        .setLifecycleOwner(requireActivity())
                        .setTextColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87))
                        .setTextTypeface(Typeface.DEFAULT)
                        .setMenuColor(ThemeHelper.getColor(requireContext(), R.color.window_background_light))
                        .setTextSize(Helper.dimensAsDp(requireContext(), R.dimen.text_subtitle_1))
                        .setAutoDismiss(true);

                if (!Preferences.getGroupingDisplay().canDeleteGroups()) {
                    // Delete books only
                    powerMenuBuilder.addItem(new PowerMenuItem(getResources().getQuantityString(R.plurals.group_delete_selected_book, selectedContent.size(), selectedContent.size()), R.drawable.ic_action_delete_forever, 0));
                } else {
                    // Delete group only
                    if (Preferences.getGroupingDisplay().canReorderGroups())
                        powerMenuBuilder.addItem(new PowerMenuItem(getResources().getQuantityString(R.plurals.group_delete_selected_group, selectedGroups.size()), R.drawable.ic_folder_delete, 1));
                    if (!selectedContent.isEmpty()) // Delete groups and books
                        powerMenuBuilder.addItem(new PowerMenuItem(getResources().getQuantityString(R.plurals.group_delete_selected_group_books, selectedGroups.size()), R.drawable.ic_action_delete_forever, 2));
                }
                powerMenuBuilder.addItem(new PowerMenuItem(getResources().getString(R.string.cancel), R.drawable.ic_close, 99));
                PowerMenu powerMenu = powerMenuBuilder.build();

                final List<Group> finalGroups = Collections.unmodifiableList(selectedGroups);
                final List<Content> finalContent = Collections.unmodifiableList(selectedContent);

                powerMenu.setOnMenuItemClickListener((position, item) -> {
                    int tag = (Integer) item.getTag();
                    if (0 == tag) { // Delete books only
                        viewModel.deleteItems(finalContent, Collections.emptyList(), false);
                    } else if (1 == tag) { // Delete group only
                        viewModel.deleteItems(Collections.emptyList(), finalGroups, true);
                    } else if (2 == tag) { // Delete groups and books
                        viewModel.deleteItems(finalContent, finalGroups, false);
                    } else {
                        selectExtension.deselect(selectExtension.getSelections()); // Cancel button
                    }
                });

                powerMenu.setIconColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87));
                powerMenu.showAtCenter(recyclerView);
            } else {
                Snackbar snackbar = Snackbar.make(recyclerView, getResources().getString(R.string.group_delete_nothing), BaseTransientBottomBar.LENGTH_LONG);
                snackbar.setAction(R.string.app_settings, v -> {
                    // Open prefs on the "storage" category
                    Intent intent = new Intent(requireActivity(), PrefsActivity.class);

                    PrefsActivityBundle.Builder builder = new PrefsActivityBundle.Builder();
                    builder.setIsStoragePrefs(true);
                    intent.putExtras(builder.getBundle());

                    requireContext().startActivity(intent);
                });
                snackbar.show();

                selectExtension.deselect(selectExtension.getSelections());
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        // Filter on delete complete event
        if (R.id.delete_service_delete != event.processId) return;
        if (ProcessEvent.EventType.COMPLETE != event.eventType) return;
        viewModel.refreshCustomGroupingAvailable();
    }

    /**
     * Callback for the "archive item" action button
     */
    private void archiveSelectedItems() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        List<Content> selectedContent = Stream.of(selectedItems)
                .map(GroupDisplayItem::getGroup)
                .withoutNulls()
                .flatMap(g -> Stream.of(viewModel.getGroupContents(g)))
                .withoutNulls()
                .filterNot(c -> c.getStorageUri().isEmpty())
                .toList();
        if (!selectedContent.isEmpty())
            activity.get().askArchiveItems(selectedContent, selectExtension);
    }

    /**
     * Callback for the "edit item name" action button
     */
    private void editSelectedItemName() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        Group g = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().findFirst().get();

        InputDialog.invokeInputDialog(requireActivity(), R.string.group_edit_name, g.name,
                this::onEditName, () -> selectExtension.deselect(selectExtension.getSelections()));
    }

    private void onEditName(@NonNull final String newName) {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        Group g = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().findFirst().get();
        viewModel.renameGroup(g, newName, stringIntRes -> {
            ToastHelper.toast(stringIntRes);
            editSelectedItemName();
        }, () -> selectExtension.setSelectOnLongClick(true));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewModel != null) viewModel.onSaveState(outState);
        if (fastAdapter != null) fastAdapter.saveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (null == savedInstanceState) return;

        if (viewModel != null) viewModel.onRestoreState(savedInstanceState);
        if (fastAdapter != null) fastAdapter.withSavedInstanceState(savedInstanceState);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onAppUpdated(AppUpdatedEvent event) {
        EventBus.getDefault().removeStickyEvent(event);
        // Display the "update success" dialog when an update is detected on a release version
        if (!BuildConfig.DEBUG) UpdateSuccessDialogFragment.invoke(getParentFragmentManager());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActivityEvent(CommunicationEvent event) {
        if (event.getRecipient() != RC_GROUPS) return;
        switch (event.getType()) {
            case EV_SEARCH:
                if (event.getMessage() != null) onSubmitSearch(event.getMessage());
                break;
            case EV_UPDATE_SORT:
                updateSortControls();
                addCustomBackControl();
                activity.get().initFragmentToolbars(selectExtension, this::onToolbarItemClicked, this::onSelectionToolbarItemClicked);
                break;
            case EV_ENABLE:
                onEnable();
                break;
            case EV_DISABLE:
                onDisable();
                break;
            default:
                // No default behaviour
        }
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        if (callback != null) callback.remove();
        super.onDestroy();
    }

    private void customBackPress() {
        // If content is selected, deselect it
        if (!selectExtension.getSelections().isEmpty()) {
            selectExtension.deselect(selectExtension.getSelections());
            activity.get().getSelectionToolbar().setVisibility(View.GONE);
            backButtonPressed = 0;
            return;
        }

        if (!activity.get().collapseSearchMenu() && !activity.get().closeLeftDrawer()) {
            // If none of the above and a search filter is on => clear search filter
            if (activity.get().isSearchQueryActive()) {
                activity.get().setQuery("");
                activity.get().setMetadata(Collections.emptyList());
                activity.get().hideSearchSortBar(false);
                viewModel.searchContent(activity.get().getQuery(), activity.get().getMetadata());
            }
            // If none of the above, user is asking to leave => use double-tap
            if (backButtonPressed + 2000 > SystemClock.elapsedRealtime()) {
                callback.remove();
                requireActivity().onBackPressed();
            } else {
                backButtonPressed = SystemClock.elapsedRealtime();
                ToastHelper.toast(R.string.press_back_again);

                llm.scrollToPositionWithOffset(0, 0);
            }
        }
    }

    /**
     * Initialize the paging method of the screen
     */
    private void setPagingMethod(/*boolean isEditMode*/) {
        viewModel.setPagingMethod(true);

        itemAdapter = new ItemAdapter<>();
        fastAdapter = FastAdapter.with(itemAdapter);
        if (!fastAdapter.hasObservers()) fastAdapter.setHasStableIds(true);

        // Gets (or creates and attaches if not yet existing) the extension from the given `FastAdapter`
        selectExtension = fastAdapter.getOrCreateExtension(SelectExtension.class);
        if (selectExtension != null) {
            selectExtension.setSelectable(true);
            selectExtension.setMultiSelect(true);
            selectExtension.setSelectOnLongClick(true);
            selectExtension.setSelectWithItemUpdate(true);
            selectExtension.setSelectionListener((i, b) -> this.onSelectionChanged());

            FastAdapterPreClickSelectHelper<GroupDisplayItem> helper = new FastAdapterPreClickSelectHelper<>(selectExtension);
            fastAdapter.setOnPreClickListener(helper::onPreClickListener);
            fastAdapter.setOnPreLongClickListener(helper::onPreLongClickListener);
        }

        // Drag, drop & swiping
        if (activity.get().isEditMode()) {
            SimpleDragCallback dragSwipeCallback = new SimpleSwipeDragCallback(
                    this,
                    this,
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_action_delete_forever)).withSensitivity(10f).withSurfaceThreshold(0.75f);
            dragSwipeCallback.setNotifyAllDrops(true);
            dragSwipeCallback.setIsDragEnabled(false); // Despite its name, that's actually to disable drag on long tap

            touchHelper = new ItemTouchHelper(dragSwipeCallback);
            touchHelper.attachToRecyclerView(recyclerView);
        }

        // Item click listener
        fastAdapter.setOnClickListener((v, a, i, p) -> onItemClick(i));

        // Favourite button click listener
        fastAdapter.addEventHook(new ClickEventHook<GroupDisplayItem>() {
            @Override
            public void onClick(@NotNull View view, int i, @NotNull FastAdapter<GroupDisplayItem> fastAdapter, @NotNull GroupDisplayItem item) {
                if (item.getGroup() != null) onGroupFavouriteClick(item.getGroup());
            }

            @org.jetbrains.annotations.Nullable
            @Override
            public View onBind(RecyclerView.@NotNull ViewHolder viewHolder) {
                if (viewHolder instanceof GroupDisplayItem.GroupViewHolder) {
                    return ((GroupDisplayItem.GroupViewHolder) viewHolder).getFavouriteButton();
                }
                return super.onBind(viewHolder);
            }
        });

        fastAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);

        recyclerView.setAdapter(fastAdapter);
        recyclerView.setHasFixedSize(true);
    }

    private void onGroupsChanged(List<Group> result) {
        Timber.i(">> Groups changed ! Size=%s", result.size());
        if (!enabled) return;

        boolean isEmpty = (result.isEmpty());
        emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        activity.get().updateTitle(result.size(), totalContentCount);

        final @GroupDisplayItem.ViewType int viewType =
                activity.get().isEditMode() ? GroupDisplayItem.ViewType.LIBRARY_EDIT :
                        (Preferences.Constant.LIBRARY_DISPLAY_LIST == Preferences.getLibraryDisplay()) ?
                                GroupDisplayItem.ViewType.LIBRARY :
                                GroupDisplayItem.ViewType.LIBRARY_GRID;

        List<GroupDisplayItem> groups = Stream.of(result).map(g -> new GroupDisplayItem(g, touchHelper, viewType)).withoutNulls().distinct().toList();
        FastAdapterDiffUtil.INSTANCE.set(itemAdapter, groups, GROUPITEM_DIFF_CALLBACK);

        // Reset library load indicator
        firstLibraryLoad = true;
    }

    /**
     * LiveData callback when the total number of groups changes (because of book download of removal)
     *
     * @param count Current group count for the currently selected grouping
     */
    private void onTotalGroupsChanged(Integer count) {
        if (!enabled) return;
        totalContentCount = count;
        activity.get().updateTitle(itemAdapter.getItemList().size(), totalContentCount);
    }

    /**
     * LiveData callback when the library changes
     * Happens when a book has been downloaded or deleted
     *
     * @param result Current library according to active filters
     */
    private void onLibraryChanged(PagedList<Content> result) {
        Timber.i(">>Library changed (groups) ! Size=%s", result.size());
        if (!enabled) return;

        // Refresh groups (new content -> updated book count or new groups)
        // TODO do we really want to do that, especially when deleting content ?
        if (!firstLibraryLoad)
            viewModel.setGrouping(
                    Preferences.getGroupingDisplay(),
                    Preferences.getGroupSortField(),
                    Preferences.isGroupSortDesc(),
                    Preferences.getArtistGroupVisibility(),
                    activity.get().isGroupFavsChecked());
        else {
            Timber.i(">>Library changed (groups) : ignored");
            firstLibraryLoad = false;
        }
    }

    // TODO doc
    private void onSubmitSearch(@NonNull final String query) {
        if (query.startsWith("http")) { // Quick-open a page
            Site s = Site.searchByUrl(query);
            if (null == s)
                Snackbar.make(recyclerView, R.string.malformed_url, BaseTransientBottomBar.LENGTH_SHORT).show();
            else if (s.equals(Site.NONE))
                Snackbar.make(recyclerView, R.string.unsupported_site, BaseTransientBottomBar.LENGTH_SHORT).show();
            else
                ContentHelper.launchBrowserFor(requireContext(), query);
        } else {
            viewModel.searchGroup(
                    Preferences.getGroupingDisplay(),
                    query, Preferences.getGroupSortField(),
                    Preferences.isGroupSortDesc(),
                    Preferences.getArtistGroupVisibility(),
                    activity.get().isGroupFavsChecked());
        }
    }

    /**
     * Callback for the group holder itself
     *
     * @param item GroupDisplayItem that has been clicked on
     */
    private boolean onItemClick(@NonNull GroupDisplayItem item) {
        if (selectExtension.getSelections().isEmpty()) {
            if (item.getGroup() != null && !item.getGroup().isBeingDeleted()) {
                activity.get().showBooksInGroup(item.getGroup());
            }
            return true;
        }
        return false;
    }

    /**
     * Callback for any selection change (item added to or removed from selection)
     */
    private void onSelectionChanged() {
        Set<GroupDisplayItem> selectedItems = selectExtension.getSelectedItems();
        int selectedCount = selectedItems.size();

        if (0 == selectedCount) {
            activity.get().getSelectionToolbar().setVisibility(View.GONE);
            selectExtension.setSelectOnLongClick(true);
        } else {
            long selectedLocalCount = Stream.of(selectedItems).map(GroupDisplayItem::getGroup).withoutNulls().count();
            activity.get().updateSelectionToolbar(selectedCount, selectedLocalCount, 0);
            activity.get().getSelectionToolbar().setVisibility(View.VISIBLE);
        }
    }

    /**
     * Calculate the position of the top visible item of the book list
     *
     * @return position of the top visible item of the book list
     */
    private int getTopItemPosition() {
        return Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());
    }

    /**
     * DRAG, DROP & SWIPE METHODS
     */

    @Override
    public boolean itemTouchOnMove(int oldPosition, int newPosition) {
        DragDropUtil.onMove(itemAdapter, oldPosition, newPosition); // change position
        return true;
    }

    @Override
    public void itemTouchDropped(int i, int i1) {
        // Nothing; final position will be saved once the "save" button is hit
    }

    @Override
    public void itemTouchStartDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        if (viewHolder instanceof IDraggableViewHolder) {
            ((IDraggableViewHolder) viewHolder).onDragged();
        }
    }

    @Override
    public void itemSwiped(int i, int i1) {
        // TODO
    }

    @Override
    public void itemTouchStopDrag(RecyclerView.@NotNull ViewHolder viewHolder) {
        // Nothing
    }
}
