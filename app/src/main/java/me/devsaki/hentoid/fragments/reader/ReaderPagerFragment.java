package me.devsaki.hentoid.fragments.reader;

import static me.devsaki.hentoid.util.Preferences.Constant;
import static me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_05;
import static me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_1;
import static me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_16;
import static me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_4;
import static me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_8;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterInside;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;
import com.skydoves.submarine.SubmarineItem;
import com.skydoves.submarine.SubmarineView;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ReaderActivity;
import me.devsaki.hentoid.adapters.ImagePagerAdapter;
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.databinding.FragmentReaderPagerBinding;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.exception.ContentNotProcessedException;
import me.devsaki.hentoid.viewmodels.ReaderViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.widget.OnZoneTapListener;
import me.devsaki.hentoid.widget.PageSnapWidget;
import me.devsaki.hentoid.widget.PrefetchLinearLayoutManager;
import me.devsaki.hentoid.widget.ReaderKeyListener;
import me.devsaki.hentoid.widget.ReaderSmoothScroller;
import me.devsaki.hentoid.widget.ScrollPositionListener;
import timber.log.Timber;

// TODO : better document and/or encapsulate the difference between
//   - paper roll mode (currently used for vertical display)
//   - independent page mode (currently used for horizontal display)
public class ReaderPagerFragment extends Fragment implements ReaderBrowseModeDialogFragment.Parent, ReaderPrefsDialogFragment.Parent, ReaderDeleteDialogFragment.Parent, ReaderNavigation.Pager {

    private static final String KEY_HUD_VISIBLE = "hud_visible";
    private static final String KEY_GALLERY_SHOWN = "gallery_shown";
    private static final String KEY_SLIDESHOW_ON = "slideshow_on";
    private static final String KEY_IMG_INDEX = "image_index";

    private final Transformation<Bitmap> centerInside = new CenterInside();
    private final RequestOptions glideRequestOptions = new RequestOptions().optionalTransform(centerInside);

    private ImagePagerAdapter adapter;
    private PrefetchLinearLayoutManager llm;
    private PageSnapWidget pageSnapWidget;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;
    private ReaderViewModel viewModel;
    private int absImageIndex = -1; // Absolute (book scale) 0-based image index
    private boolean hasGalleryBeenShown = false;
    private final ScrollPositionListener scrollListener = new ScrollPositionListener(this::onScrollPositionChange);
    private Disposable slideshowTimer = null;
    private boolean isSlideshowActive = false;

    // Properties
    private Map<String, String> bookPreferences; // Preferences of current book; to feed the book prefs dialog
    private boolean isContentArchive; // True if current content is an archive
    private boolean isPageFavourite; // True if current page is favourited
    private boolean isContentFavourite; // True if current content is favourited

    private Debouncer<Integer> indexRefreshDebouncer;
    private Debouncer<Pair<Integer, Integer>> processPositionDebouncer;
    private Debouncer<Float> rescaleDebouncer;

    // Starting index management
    private boolean isComputingImageList = false;
    private int targetStartingIndex = -1;
    private boolean startingIndexLoaded = false;
    private long contentId = -1;

    // == UI ==
    private FragmentReaderPagerBinding binding = null;
    private ReaderSmoothScroller smoothScroller;

    // Top menu items
    private MenuItem showFavoritePagesButton;
    private MenuItem shuffleButton;

    private ReaderNavigation navigator;

    // Debouncer for the slideshow slider
    private Debouncer<Integer> slideshowSliderDebouncer;


    @SuppressLint("NonConstantResourceId")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentReaderPagerBinding.inflate(inflater, container, false);

        indexRefreshDebouncer = new Debouncer<>(requireContext(), 75, this::applyStartingIndexInternal);
        slideshowSliderDebouncer = new Debouncer<>(requireContext(), 2500, this::onSlideShowSliderChosen);
        processPositionDebouncer = new Debouncer<>(requireContext(), 500, pair -> viewModel.onPageChange(pair.getLeft(), pair.getRight()));
        rescaleDebouncer = new Debouncer<>(requireContext(), 100, scale -> adapter.multiplyScale(scale));

        Preferences.registerPrefsChangedListener(listener);

        initPager();
        initControlsOverlay();

        onUpdateSwipeToFling();
        onUpdatePageNumDisplay();
        onUpdateSwipeToTurn();

        // Top bar controls
        Helper.tryShowMenuIcons(requireActivity(), binding.controlsOverlay.viewerPagerToolbar.getMenu());
        binding.controlsOverlay.viewerPagerToolbar.setNavigationOnClickListener(v -> onBackClick());
        binding.controlsOverlay.viewerPagerToolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            switch (clickedMenuItem.getItemId()) {
                case R.id.action_show_favorite_pages:
                    onShowFavouriteClick();
                    break;
                case R.id.action_book_settings:
                    onBookSettingsClick();
                    break;
                case R.id.action_shuffle:
                    onShuffleClick();
                    break;
                case R.id.action_slideshow:
                    int startIndex;
                    if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                        startIndex = Preferences.getViewerSlideshowDelayVertical();
                    else startIndex = Preferences.getViewerSlideshowDelay();
                    startIndex = convertPrefsDelayToSliderPosition(startIndex);

                    binding.controlsOverlay.slideshowDelaySlider.setValue(startIndex);
                    binding.controlsOverlay.slideshowDelaySlider.setLabelBehavior(LabelFormatter.LABEL_FLOATING);
                    binding.controlsOverlay.slideshowDelaySlider.setVisibility(View.VISIBLE);
                    slideshowSliderDebouncer.submit(startIndex);
                    break;
                case R.id.action_delete_book:
                    if (Constant.VIEWER_DELETE_ASK_AGAIN == Preferences.getViewerDeleteAskMode())
                        ReaderDeleteDialogFragment.invoke(this, !isContentArchive);
                    else // We already know what to delete
                        onDeleteElement(Constant.VIEWER_DELETE_TARGET_PAGE == Preferences.getViewerDeleteTarget());
                    break;
                default:
                    // Nothing to do here
            }
            return true;
        });
        showFavoritePagesButton = binding.controlsOverlay.viewerPagerToolbar.getMenu().findItem(R.id.action_show_favorite_pages);
        shuffleButton = binding.controlsOverlay.viewerPagerToolbar.getMenu().findItem(R.id.action_shuffle);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        indexRefreshDebouncer.clear();
        slideshowSliderDebouncer.clear();
        processPositionDebouncer.clear();
        rescaleDebouncer.clear();
        navigator.clear();
        binding.recyclerView.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ReaderViewModel.class);

//        viewModel.onRestoreState(savedInstanceState);

        viewModel.getContent().observe(getViewLifecycleOwner(), this::onContentChanged);

        viewModel.getViewerImages().observe(getViewLifecycleOwner(), this::onImagesChanged);

        viewModel.getStartingIndex().observe(getViewLifecycleOwner(), this::onStartingIndexChanged);

        viewModel.getShuffled().observe(getViewLifecycleOwner(), this::onShuffleChanged);

        viewModel.getShowFavouritesOnly().observe(getViewLifecycleOwner(), this::updateShowFavouriteDisplay);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null)
            outState.putInt(KEY_HUD_VISIBLE, binding.controlsOverlay.getRoot().getVisibility());
        outState.putBoolean(KEY_SLIDESHOW_ON, isSlideshowActive);
        outState.putBoolean(KEY_GALLERY_SHOWN, hasGalleryBeenShown);
        // Memorize the current page
        outState.putInt(KEY_IMG_INDEX, absImageIndex);
        if (viewModel != null) viewModel.setViewerStartingIndex(absImageIndex);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        int hudVisibility = View.INVISIBLE; // Default state at startup
        if (savedInstanceState != null) {
            hudVisibility = savedInstanceState.getInt(KEY_HUD_VISIBLE, View.INVISIBLE);
            hasGalleryBeenShown = savedInstanceState.getBoolean(KEY_GALLERY_SHOWN, false);
            absImageIndex = savedInstanceState.getInt(KEY_IMG_INDEX, -1);
            if (savedInstanceState.getBoolean(KEY_SLIDESHOW_ON, false)) startSlideshow(false);
        }
        binding.controlsOverlay.getRoot().setVisibility(hudVisibility);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        ((ReaderActivity) requireActivity()).registerKeyListener(new ReaderKeyListener(requireContext()).setOnVolumeDownListener(b -> {
            if (b && Preferences.isViewerVolumeToSwitchBooks()) navigator.previousFunctional();
            else previousPage();
        }).setOnVolumeUpListener(b -> {
            if (b && Preferences.isViewerVolumeToSwitchBooks()) navigator.nextFunctional();
            else nextPage();
        }).setOnKeyLeftListener(b -> onLeftTap()).setOnKeyRightListener(b -> onRightTap()).setOnBackListener(b -> onBackClick()));
    }

    @Override
    public void onResume() {
        super.onResume();

        setSystemBarsVisible(binding.controlsOverlay.getRoot().getVisibility() == View.VISIBLE); // System bars are visible only if HUD is visible
        if (Preferences.Constant.VIEWER_BROWSE_NONE == Preferences.getViewerBrowseMode())
            ReaderBrowseModeDialogFragment.invoke(this);
        navigator.updatePageControls();
    }

    // Make sure position is saved when app is closed by the user
    @Override
    public void onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        viewModel.onLeaveBook(absImageIndex);
        if (slideshowTimer != null) slideshowTimer.dispose();
        ((ReaderActivity) requireActivity()).unregisterKeyListener();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(listener);
        if (adapter != null) {
            adapter.setRecyclerView(null);
            adapter.destroy();
        }
        super.onDestroy();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onProcessEvent(ProcessEvent event) {
        if (null == binding) return;
        if (event.processId != R.id.viewer_load && event.processId != R.id.viewer_page_download)
            return;
        if (event.processId == R.id.viewer_page_download && event.step != absImageIndex) return;

        processEvent(event);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onProcessStickyEvent(ProcessEvent event) {
        if (null == binding) return;
        if (event.processId != R.id.viewer_load && event.processId != R.id.viewer_page_download)
            return;
        if (event.processId == R.id.viewer_page_download && event.step != absImageIndex) return;
        EventBus.getDefault().removeStickyEvent(event);
        processEvent(event);
    }

    private void processEvent(ProcessEvent event) {
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            @StringRes int msgResource = R.string.loading_image;
            if (event.processId == R.id.viewer_load) { // Archive unpacking
                msgResource = R.string.loading_archive;
            }

            binding.viewerFixBtn.setVisibility(View.GONE);
            binding.viewerLoadingTxt.setText(getResources().getString(msgResource, event.elementsKO + event.elementsOK, event.elementsTotal));
            if (event.elementsOK + event.elementsKO < 5)
                binding.viewerLoadingTxt.setVisibility(View.VISIBLE); // Just show it for the first iterations to allow the UI to hide it when it wants

            binding.progressBar.setMax(event.elementsTotal);
            binding.controlsOverlay.progressBar.setMax(event.elementsTotal);
            binding.progressBar.setProgress(event.elementsKO + event.elementsOK);
            binding.controlsOverlay.progressBar.setProgress(event.elementsKO + event.elementsOK);
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.controlsOverlay.progressBar.setVisibility(View.VISIBLE);
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType) {
            binding.viewerLoadingTxt.setVisibility(View.GONE);
            binding.progressBar.setVisibility(View.GONE);
            binding.controlsOverlay.progressBar.setVisibility(View.GONE);
        } else if (ProcessEvent.EventType.FAILURE == event.eventType) {
            binding.viewerLoadingTxt.setVisibility(View.GONE);
            binding.progressBar.setVisibility(View.GONE);
            binding.controlsOverlay.progressBar.setVisibility(View.GONE);
            binding.viewerFixBtn.setVisibility(View.VISIBLE);
        }
    }

    private void initPager() {
        adapter = new ImagePagerAdapter(requireContext());

        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.addOnScrollListener(scrollListener);
        binding.recyclerView.setOnGetMaxDimensionsListener(this::onGetMaxDimensions);
        binding.recyclerView.requestFocus();
        binding.recyclerView.setOnScaleListener(scale -> {
            if (pageSnapWidget != null && LinearLayoutManager.HORIZONTAL == llm.getOrientation()) {
                if (1.0 == scale && !pageSnapWidget.isPageSnapEnabled())
                    pageSnapWidget.setPageSnapEnabled(true);
                else if (1.0 != scale && pageSnapWidget.isPageSnapEnabled())
                    pageSnapWidget.setPageSnapEnabled(false);
            }
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                rescaleDebouncer.submit((float) scale);
        });
        binding.recyclerView.setLongTapListener(ev -> {
            onLongTap();
            return false;
        });

        int tapZoneScale = Preferences.isViewerTapToTurn2x() ? 2 : 1;

        OnZoneTapListener onHorizontalZoneTapListener = new OnZoneTapListener(binding.recyclerView, tapZoneScale).setOnLeftZoneTapListener(this::onLeftTap).setOnRightZoneTapListener(this::onRightTap).setOnMiddleZoneTapListener(this::onMiddleTap).setOnLongTapListener(this::onLongTap);

        OnZoneTapListener onVerticalZoneTapListener = new OnZoneTapListener(binding.recyclerView, 1).setOnMiddleZoneTapListener(this::onMiddleTap).setOnLongTapListener(this::onLongTap);

        binding.recyclerView.setTapListener(onVerticalZoneTapListener);       // For paper roll mode (vertical)
        adapter.setItemTouchListener(onHorizontalZoneTapListener);    // For independent images mode (horizontal)

        adapter.setRecyclerView(binding.recyclerView);

        llm = new PrefetchLinearLayoutManager(getContext());
        llm.setExtraLayoutSpace(10);
        binding.recyclerView.setLayoutManager(llm);

        pageSnapWidget = new PageSnapWidget(binding.recyclerView);

        smoothScroller = new ReaderSmoothScroller(requireContext());

        scrollListener.setOnStartOutOfBoundScrollListener(() -> {
            if (Preferences.isViewerContinuous()) navigator.previousFunctional();
        });
        scrollListener.setOnEndOutOfBoundScrollListener(() -> {
            if (Preferences.isViewerContinuous()) navigator.nextFunctional();
        });
    }

    private void initControlsOverlay() {
        // Slideshow slider
        Slider slider = binding.controlsOverlay.slideshowDelaySlider;
        slider.setValueFrom(0);
        int sliderValue;
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
            sliderValue = convertPrefsDelayToSliderPosition(Preferences.getViewerSlideshowDelayVertical());
        else sliderValue = convertPrefsDelayToSliderPosition(Preferences.getViewerSlideshowDelay());

        int nbEntries = getResources().getStringArray(R.array.pref_viewer_slideshow_delay_entries).length;
        nbEntries = Math.max(1, nbEntries - 1);
        // TODO at some point we'd need to better synch images and book loading to avoid that
        slider.setValue(Helper.coerceIn(sliderValue, 0, nbEntries));
        slider.setValueTo(nbEntries);
        slider.setLabelFormatter(value -> {
            String[] entries;
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences)) {
                entries = getResources().getStringArray(R.array.pref_viewer_slideshow_delay_entries_vertical);
            } else {
                entries = getResources().getStringArray(R.array.pref_viewer_slideshow_delay_entries);
            }
            return entries[(int) value];
        });
        slider.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) slider.setVisibility(View.GONE);
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                slideshowSliderDebouncer.clear();
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                onSlideShowSliderChosen((int) slider.getValue());
            }
        });

        // Fix page button
        binding.viewerFixBtn.setOnClickListener(v -> fixPage());

        // Bottom navigation controls
        navigator = new ReaderNavigation(this, binding);

        // Information micro menu
        binding.controlsOverlay.informationMicroMenu.setSubmarineItemClickListener((p, i) -> onInfoMicroMenuClick(p));
        binding.controlsOverlay.informationMicroMenu.addSubmarineItem(new SubmarineItem(ContextCompat.getDrawable(requireContext(), R.drawable.ic_book)/*, null, getResources().getString(R.string.book_details)*/));
        binding.controlsOverlay.informationMicroMenu.addSubmarineItem(new SubmarineItem(ContextCompat.getDrawable(requireContext(), R.drawable.ic_page)/*, null, getResources().getString(R.string.page_details)*/));
        binding.controlsOverlay.viewerInfoBtn.setOnClickListener(v -> {
            binding.controlsOverlay.favouriteMicroMenu.dips();
            binding.controlsOverlay.informationMicroMenu.floats();
        });
        binding.controlsOverlay.informationMicroMenu.setSubmarineCircleClickListener(binding.controlsOverlay.favouriteMicroMenu::dips);

        // Favourite micro menu
        updateFavouriteButtonIcon();

        binding.controlsOverlay.favouriteMicroMenu.setSubmarineItemClickListener((p, i) -> onFavouriteMicroMenuClick(p));
        binding.controlsOverlay.viewerFavouriteActionBtn.setOnClickListener(v -> onFavouriteMicroMenuOpen());
        binding.controlsOverlay.favouriteMicroMenu.setSubmarineCircleClickListener(binding.controlsOverlay.favouriteMicroMenu::dips);

        // Gallery
        binding.controlsOverlay.viewerGalleryBtn.setOnClickListener(v -> displayGallery());
    }

    private int convertPrefsDelayToSliderPosition(int prefsDelay) {
        List<Integer> prefsValues = Stream.of(getResources().getStringArray(R.array.pref_viewer_slideshow_delay_values)).map(Integer::parseInt).toList();
        for (int i = 0; i < prefsValues.size(); i++)
            if (prefsValues.get(i) == prefsDelay) return i;

        return 0;
    }

    private int convertSliderPositionToPrefsDelay(int sliderPosition) {
        List<Integer> prefsValues = Stream.of(getResources().getStringArray(R.array.pref_viewer_slideshow_delay_values)).map(Integer::parseInt).toList();
        return prefsValues.get(sliderPosition);
    }

    /**
     * Back button handler
     */
    private void onBackClick() {
        requireActivity().onBackPressed();
    }

    /**
     * Show the book viewer settings dialog
     */
    private void onBookSettingsClick() {
        ReaderPrefsDialogFragment.invoke(this, bookPreferences);
    }

    /**
     * Handle click on "Shuffle" action button
     */
    private void onShuffleClick() {
        goToPage(1);
        viewModel.toggleShuffle();
    }

    /**
     * Handle click on "Show favourite pages" toggle action button
     */
    private void onShowFavouriteClick() {
        viewModel.filterFavouriteImages(!showFavoritePagesButton.isChecked());
    }

    /**
     * Update the display of the "favourite page" action button
     *
     * @param showFavouritePages True if the button has to represent a favourite page; false instead
     */
    private void updateShowFavouriteDisplay(boolean showFavouritePages) {
        showFavoritePagesButton.setChecked(showFavouritePages);
        if (showFavouritePages) {
            showFavoritePagesButton.setIcon(R.drawable.ic_filter_favs_on);
            showFavoritePagesButton.setTitle(R.string.viewer_filter_favourite_on);
        } else {
            showFavoritePagesButton.setIcon(R.drawable.ic_filter_favs_off);
            showFavoritePagesButton.setTitle(R.string.viewer_filter_favourite_off);
        }
    }

    /**
     * Handle click on "Information" micro menu
     */
    private void onInfoMicroMenuClick(int position) {
        if (0 == position) { // Content
            ReaderBottomContentFragment.invoke(requireContext(), requireActivity().getSupportFragmentManager());
        } else { // Image
            float currentScale = adapter.getAbsoluteScaleAtPosition(absImageIndex);
            ReaderBottomImageFragment.invoke(requireContext(), requireActivity().getSupportFragmentManager(), absImageIndex, currentScale);
        }
        binding.controlsOverlay.informationMicroMenu.dips();
    }

    private void onFavouriteMicroMenuOpen() {
        binding.controlsOverlay.informationMicroMenu.dips();

        SubmarineView favMenu = binding.controlsOverlay.favouriteMicroMenu;
        favMenu.clearAllSubmarineItems();
        favMenu.addSubmarineItem(new SubmarineItem(ContextCompat.getDrawable(requireContext(), isContentFavourite ? R.drawable.ic_book_fav : R.drawable.ic_book)/*,
                        null,
                        getResources().getString(R.string.book_favourite_toggle)*/));
        favMenu.addSubmarineItem(new SubmarineItem(ContextCompat.getDrawable(requireContext(), isPageFavourite ? R.drawable.ic_page_fav : R.drawable.ic_page)/*,
                        null,
                        getResources().getString(R.string.page_favourite_toggle)*/));
        favMenu.floats();
    }

    /**
     * Handle click on one of the "Favourite" micro menu items
     */
    private void onFavouriteMicroMenuClick(int position) {
        if (0 == position) viewModel.toggleContentFavourite(this::onBookFavouriteSuccess);
        else if (1 == position)
            viewModel.toggleImageFavourite(this.absImageIndex, this::onPageFavouriteSuccess);

        binding.controlsOverlay.favouriteMicroMenu.dips();
    }

    private void updateFavouriteButtonIcon() {
        @DrawableRes int iconRes = R.drawable.ic_fav_empty;
        if (isPageFavourite) {
            if (isContentFavourite) iconRes = R.drawable.ic_fav_full;
            else iconRes = R.drawable.ic_fav_bottom_half;
        } else if (isContentFavourite) iconRes = R.drawable.ic_fav_top_half;
        binding.controlsOverlay.viewerFavouriteActionBtn.setImageResource(iconRes);
    }

    private void hidePendingMicroMenus() {
        binding.controlsOverlay.informationMicroMenu.dips();
        binding.controlsOverlay.favouriteMicroMenu.dips();
    }

    private void onPageFavouriteSuccess(boolean newState) {
        ToastHelper.toast(newState ? R.string.page_favourite_success : R.string.page_unfavourite_success);
        isPageFavourite = newState;
        updateFavouriteButtonIcon();
    }

    private void onBookFavouriteSuccess(boolean newState) {
        ToastHelper.toast(newState ? R.string.book_favourite_success : R.string.book_unfavourite_success);
        isContentFavourite = newState;
        updateFavouriteButtonIcon();
    }

    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private void onImagesChanged(List<ImageFile> images) {
        if (BuildConfig.DEBUG) {
            Timber.v("IMAGES CHANGED");
            List<String> imageUris = Stream.of(images).filterNot(img -> img.getFileUri().isEmpty()).map(img -> "[" + img.getOrder() + "] " + img.getFileUri()).toList();
            for (String imageUri : imageUris) Timber.v("    %s", imageUri);
        }

        isComputingImageList = true;
        adapter.reset();
        adapter.submitList(images, this::differEndCallback);

        if (images.isEmpty()) {
            setSystemBarsVisible(true);
            binding.viewerNoImgTxt.setVisibility(View.VISIBLE);
        } else if (absImageIndex > -1 && absImageIndex < images.size()) {
            isPageFavourite = images.get(absImageIndex).isFavourite();
            updateFavouriteButtonIcon();
            binding.viewerNoImgTxt.setVisibility(View.GONE);
            binding.viewerLoadingTxt.setVisibility(View.GONE);
        }
    }

    /**
     * Callback for the end of image list diff calculations
     * Activated when all displayed items are placed on their definitive position
     */
    private void differEndCallback() {
        if (null == binding) return;

        navigator.onImagesChanged(adapter.getCurrentList());

        if (targetStartingIndex > -1) applyStartingIndex(targetStartingIndex);
        else navigator.updatePageControls();

        isComputingImageList = false;
    }

    /**
     * Observer for changes on the book's starting image index
     *
     * @param startingIndex Book's starting image index
     */
    private void onStartingIndexChanged(Integer startingIndex) {
        if (!isComputingImageList)
            applyStartingIndex(startingIndex); // Returning from gallery screen
        else targetStartingIndex = startingIndex; // Loading a new book
    }

    private void applyStartingIndex(int absStartingIndex) {
        indexRefreshDebouncer.submit(absStartingIndex);
        targetStartingIndex = -1;
    }

    private void applyStartingIndexInternal(int startingIndex) {
        startingIndexLoaded = true;
        int currentPosition = Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());

        // When target position is the same as current scroll index (0), scrolling is pointless
        // -> activate scroll listener manually
        if (currentPosition == startingIndex) onScrollPositionChange(startingIndex);
        else {
            if (LinearLayoutManager.HORIZONTAL == llm.getOrientation() && binding != null)
                binding.recyclerView.scrollToPosition(startingIndex);
            else llm.scrollToPositionWithOffset(startingIndex, 0);
        }
    }

    /**
     * Observer for changes on the current book
     *
     * @param content Loaded book
     */
    private void onContentChanged(Content content) {
        if (null == content) {
            onBackClick();
            return;
        }
        bookPreferences = content.getBookPreferences();
        isContentArchive = content.isArchive();
        isContentFavourite = content.isFavourite();
        // Wait for starting index only if content actually changes
        if (content.getId() != contentId) startingIndexLoaded = false;
        contentId = content.getId();
        onBrowseModeChange(); // TODO check if this can be optimized, as images are loaded twice when a new book is loaded

        navigator.onContentChanged(content);
        updateFavouriteButtonIcon();
    }

    /**
     * Observer for changes on the shuffled state
     *
     * @param isShuffled New shuffled state
     */
    private void onShuffleChanged(boolean isShuffled) {
        if (isShuffled) {
            shuffleButton.setIcon(R.drawable.ic_menu_sort_123);
            shuffleButton.setTitle(R.string.viewer_order_123);
        } else {
            shuffleButton.setIcon(R.drawable.ic_menu_sort_random);
            shuffleButton.setTitle(R.string.viewer_order_shuffle);
        }
    }

    @Override
    public void onDeleteElement(boolean deletePage) {
        if (deletePage) viewModel.deletePage(absImageIndex, this::onDeleteError);
        else viewModel.deleteContent(this::onDeleteError);
    }


    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotProcessedException) {
            ContentNotProcessedException e = (ContentNotProcessedException) t;
            String message = (null == e.getMessage()) ? getString(R.string.content_removal_failed) : e.getMessage();
            Snackbar.make(binding.recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
    }


    /**
     * Scroll / page change listener
     *
     * @param scrollPosition New 0-based scroll position
     */
    private void onScrollPositionChange(int scrollPosition) {
        if (null == binding) return;
        if (!startingIndexLoaded) return;

        if (scrollPosition != absImageIndex) {
            boolean isScrollLTR = true;
            int direction = Preferences.getContentDirection(bookPreferences);
            if (Constant.VIEWER_DIRECTION_LTR == direction && absImageIndex > scrollPosition)
                isScrollLTR = false;
            else if (Constant.VIEWER_DIRECTION_RTL == direction && absImageIndex < scrollPosition)
                isScrollLTR = false;
            adapter.setScrollLTR(isScrollLTR);
            hidePendingMicroMenus();

            // Manage scaling reset / stability if we're using horizontal (independent pages) mode
            if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences)) {
                if (Preferences.isViewerMaintainHorizontalZoom() && absImageIndex > -1) {
                    float previousScale = adapter.getRelativeScaleAtPosition(absImageIndex);
                    Timber.d(">> relative scale : %s", previousScale);
                    if (previousScale > 0)
                        adapter.setRelativeScaleAtPosition(scrollPosition, previousScale);
                } else {
                    adapter.resetScaleAtPosition(scrollPosition);
                }
            }

            // Don't show loading progress from previous image
            binding.viewerLoadingTxt.setVisibility(View.GONE);
            binding.viewerFixBtn.setVisibility(View.GONE);
        }

        int scrollDirection = scrollPosition - absImageIndex;
        absImageIndex = scrollPosition;
        ImageFile currentImage = adapter.getImageAt(absImageIndex);
        if (currentImage != null) {
            Preferences.setViewerCurrentPageNum(currentImage.getOrder());
            viewModel.markPageAsRead(currentImage.getOrder());
            // Remember the last relevant movement and schedule it for execution
            processPositionDebouncer.submit(new ImmutablePair<>(absImageIndex, scrollDirection));
            isPageFavourite = currentImage.isFavourite();
        }

        navigator.updatePageControls();
        updateFavouriteButtonIcon();
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param prefs Shared preferences object
     * @param key   Key that has been changed
     */
    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch (key) {
            case Preferences.Key.VIEWER_BROWSE_MODE:
            case Preferences.Key.VIEWER_HOLD_TO_ZOOM:
            case Preferences.Key.VIEWER_CONTINUOUS:
                onBrowseModeChange();
                break;
            case Preferences.Key.VIEWER_KEEP_SCREEN_ON:
                onUpdatePrefsScreenOn();
                break;
            case Preferences.Key.VIEWER_ZOOM_TRANSITIONS:
            case Preferences.Key.VIEWER_SEPARATING_BARS:
            case Preferences.Key.VIEWER_IMAGE_DISPLAY:
            case Preferences.Key.VIEWER_AUTO_ROTATE:
            case Preferences.Key.VIEWER_RENDERING:
                onUpdateImageDisplay();
                break;
            case Preferences.Key.VIEWER_SWIPE_TO_FLING:
                onUpdateSwipeToFling();
                break;
            case Preferences.Key.VIEWER_DISPLAY_PAGENUM:
                onUpdatePageNumDisplay();
                break;
            case Preferences.Key.VIEWER_PAGE_TURN_SWIPE:
                onUpdateSwipeToTurn();
                break;
            default:
                // Other changes aren't handled here
        }
    }

    public void onBookPreferenceChanged(@NonNull final Map<String, String> newPrefs) {
        viewModel.updateContentPreferences(newPrefs);
        bookPreferences = newPrefs;
        onBrowseModeChange();
    }

    private void onUpdatePrefsScreenOn() {
        if (Preferences.isViewerKeepScreenOn())
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onUpdateSwipeToFling() {
        int flingFactor = Preferences.isViewerSwipeToFling() ? 75 : 0;
        pageSnapWidget.setFlingSensitivity(flingFactor / 100f);
    }

    private void onUpdateSwipeToTurn() {
        if (Preferences.isViewerSwipeToTurn()) scrollListener.enableScroll();
        else scrollListener.disableScroll();
    }

    /**
     * Re-create and re-bind all Viewholders
     */
    @SuppressLint("NotifyDataSetChanged")
    private void onUpdateImageDisplay() {
        adapter.refreshPrefs(bookPreferences);

        // Needs ARGB_8888 to be able to resize images using RenderScript
        if (Preferences.isContentSmoothRendering(bookPreferences))
            CustomSubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);
        else CustomSubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.RGB_565);

        binding.recyclerView.setAdapter(null);
        binding.recyclerView.setLayoutManager(null);
        binding.recyclerView.getRecycledViewPool().clear();
        binding.recyclerView.swapAdapter(adapter, false);
        binding.recyclerView.setLayoutManager(llm);
        adapter.notifyDataSetChanged(); // NB : will re-run onBindViewHolder for all displayed pictures

        seekToPosition(absImageIndex);
    }

    private void onUpdatePageNumDisplay() {
        binding.viewerPagenumberText.setVisibility(Preferences.isViewerDisplayPageNum() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBrowseModeChange() {
        int currentLayoutDirection;
        // LinearLayoutManager.setReverseLayout behaves _relatively_ to current Layout Direction
        // => need to know that direction before deciding how to set setReverseLayout
        if (View.LAYOUT_DIRECTION_LTR == binding.controlsOverlay.getRoot().getLayoutDirection())
            currentLayoutDirection = Preferences.Constant.VIEWER_DIRECTION_LTR;
        else currentLayoutDirection = Preferences.Constant.VIEWER_DIRECTION_RTL;
        llm.setReverseLayout(Preferences.getContentDirection(bookPreferences) != currentLayoutDirection);

        int orientation = Preferences.getContentOrientation(bookPreferences);
        llm.setOrientation(getOrientation(orientation));

        // Resets the views to switch between paper roll mode (vertical) and independent page mode (horizontal)
        binding.recyclerView.resetScale();
        onUpdateImageDisplay();

        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == orientation) {
            binding.zoomFrame.enable();
            binding.recyclerView.setLongTapZoomEnabled(Preferences.isViewerHoldToZoom());
        } else {
            binding.zoomFrame.disable();
            binding.recyclerView.setLongTapZoomEnabled(!Preferences.isViewerHoldToZoom());
        }

        pageSnapWidget.setPageSnapEnabled(Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == orientation);

        int direction = Preferences.getContentDirection(bookPreferences);
        adapter.setScrollLTR(Constant.VIEWER_DIRECTION_LTR == direction);
        navigator.setDirection(direction);
    }

    /**
     * Transforms current Preferences orientation into LinearLayoutManager orientation code
     *
     * @return Preferred orientation, as LinearLayoutManager orientation code
     */
    private int getOrientation(int orientation) {
        if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == orientation) {
            return LinearLayoutManager.HORIZONTAL;
        } else {
            return LinearLayoutManager.VERTICAL;
        }
    }

    /**
     * Handler for the "fix" button
     */
    private void fixPage() {
        PowerMenu.Builder powerMenuBuilder = new PowerMenu.Builder(requireContext()).setWidth(getResources().getDimensionPixelSize(R.dimen.dialog_width)).setAnimation(MenuAnimation.SHOW_UP_CENTER).setMenuRadius(10f).setIsMaterial(true).setLifecycleOwner(requireActivity()).setTextColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87)).setTextTypeface(Typeface.DEFAULT).setMenuColor(ThemeHelper.getColor(requireContext(), R.color.window_background_light)).setTextSize(Helper.dimensAsDp(requireContext(), R.dimen.text_subtitle_1)).setAutoDismiss(true);

        powerMenuBuilder.addItem(new PowerMenuItem(getResources().getString(R.string.viewer_reload_page), false, R.drawable.ic_action_refresh, null, null, 0));
        powerMenuBuilder.addItem(new PowerMenuItem(getResources().getString(R.string.viewer_reparse_book), false, R.drawable.ic_attribute_source, null, null, 1));

        PowerMenu powerMenu = powerMenuBuilder.build();

        powerMenu.setOnMenuItemClickListener((position, item) -> {
            if (item.tag != null) {
                int tag = (Integer) item.tag;
                if (0 == tag) {
                    viewModel.onPageChange(absImageIndex, 0);
                } else if (1 == tag) {
                    viewModel.reparseBook(t -> {
                        Timber.w(t);
                        binding.viewerLoadingTxt.setText(getResources().getString(R.string.redownloaded_error));
                    });
                    binding.viewerLoadingTxt.setText(getResources().getString(R.string.please_wait));
                    binding.viewerLoadingTxt.setVisibility(View.VISIBLE);
                }
            }
        });

        powerMenu.setIconColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87));
        powerMenu.showAtCenter(binding.recyclerView);
    }

    /**
     * Load next page
     */
    private void nextPage() {
        if (absImageIndex == adapter.getItemCount() - 1) {
            if (Preferences.isViewerContinuous()) navigator.nextFunctional();
            return;
        }

        if (Preferences.isViewerTapTransitions()) {
            if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences))
                binding.recyclerView.smoothScrollToPosition(absImageIndex + 1);
            else {
                smoothScroller.setTargetPosition(absImageIndex + 1);
                llm.startSmoothScroll(smoothScroller);
            }
        } else {
            if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences))
                binding.recyclerView.scrollToPosition(absImageIndex + 1);
            else llm.scrollToPositionWithOffset(absImageIndex + 1, 0);
        }
    }

    /**
     * Load previous page
     */
    private void previousPage() {
        if (0 == absImageIndex) {
            if (Preferences.isViewerContinuous()) navigator.previousFunctional();
            return;
        }

        if (Preferences.isViewerTapTransitions())
            binding.recyclerView.smoothScrollToPosition(absImageIndex - 1);
        else binding.recyclerView.scrollToPosition(absImageIndex - 1);
    }

    /**
     * Seek to the given position; update preview images if they are visible
     *
     * @param absIndex Index to go to (0-indexed; books-scale)
     */
    @Override
    public void seekToPosition(int absIndex) {
        // Hide pending micro-menus
        hidePendingMicroMenus();

        if (View.VISIBLE == binding.controlsOverlay.imagePreviewCenter.getVisibility()) {
            ImageView previousImageView;
            ImageView nextImageView;
            if (Constant.VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences)) {
                previousImageView = binding.controlsOverlay.imagePreviewLeft;
                nextImageView = binding.controlsOverlay.imagePreviewRight;
            } else {
                previousImageView = binding.controlsOverlay.imagePreviewRight;
                nextImageView = binding.controlsOverlay.imagePreviewLeft;
            }

            ImageFile previousImg = adapter.getImageAt(absIndex - 1);
            ImageFile currentImg = adapter.getImageAt(absIndex);
            ImageFile nextImg = adapter.getImageAt(absIndex + 1);

            if (previousImg != null) {
                Glide.with(previousImageView).load(Uri.parse(previousImg.getFileUri())).apply(glideRequestOptions).into(previousImageView);
                previousImageView.setVisibility(View.VISIBLE);
            } else previousImageView.setVisibility(View.INVISIBLE);

            if (currentImg != null)
                Glide.with(binding.controlsOverlay.imagePreviewCenter).load(Uri.parse(currentImg.getFileUri())).apply(glideRequestOptions).into(binding.controlsOverlay.imagePreviewCenter);

            if (nextImg != null) {
                Glide.with(nextImageView).load(Uri.parse(nextImg.getFileUri())).apply(glideRequestOptions).into(nextImageView);
                nextImageView.setVisibility(View.VISIBLE);
            } else nextImageView.setVisibility(View.INVISIBLE);
        }

        if (absIndex == absImageIndex + 1 || absIndex == absImageIndex - 1) {
            binding.recyclerView.smoothScrollToPosition(absIndex);
        } else {
            binding.recyclerView.scrollToPosition(absIndex);
        }
    }

    @Override
    public void nextBook() {
        viewModel.loadNextContent(absImageIndex);
    }

    @Override
    public void previousBook() {
        viewModel.loadPreviousContent(absImageIndex);
    }

    @Override
    public ImageFile getCurrentImg() {
        ImageFile img = adapter.getImageAt(absImageIndex);
        if (null == img) Timber.w("No image at absolute position %s", absImageIndex);
        return img;
    }

    /**
     * Go to the given page number
     *
     * @param absPageNum Asbolute page number to go to (1-indexed; book-scale)
     */
    @Override
    public void goToPage(int absPageNum) {
        int position = absPageNum - 1;
        if (position == absImageIndex || position < 0 || position > adapter.getItemCount() - 1)
            return;
        seekToPosition(position);
    }

    /**
     * Handler for tapping on the left zone of the screen
     */
    private void onLeftTap() {
        if (null == binding) return;

        // Hide pending micro-menus
        hidePendingMicroMenus();

        // Stop slideshow if it is on
        if (isSlideshowActive) {
            stopSlideshow();
            return;
        }

        // Side-tapping disabled when view is zoomed
        if (binding.recyclerView.getScale() != 1.0) return;
        // Side-tapping disabled when disabled in preferences
        if (!Preferences.isViewerTapToTurn()) return;

        if (Preferences.Constant.VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences))
            previousPage();
        else nextPage();
    }

    /**
     * Handler for tapping on the right zone of the screen
     */
    private void onRightTap() {
        if (null == binding) return;

        // Hide pending micro-menus
        hidePendingMicroMenus();

        // Stop slideshow if it is on
        if (isSlideshowActive) {
            stopSlideshow();
            return;
        }

        // Side-tapping disabled when view is zoomed
        if (binding.recyclerView.getScale() != 1.0) return;
        // Side-tapping disabled when disabled in preferences
        if (!Preferences.isViewerTapToTurn()) return;

        if (Preferences.Constant.VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences))
            nextPage();
        else previousPage();
    }

    /**
     * Handler for tapping on the middle zone of the screen
     */
    private void onMiddleTap() {
        if (null == binding) return;

        // Hide pending micro-menus
        hidePendingMicroMenus();

        // Stop slideshow if it is on
        if (isSlideshowActive) {
            stopSlideshow();
            return;
        }

        if (View.VISIBLE == binding.controlsOverlay.getRoot().getVisibility())
            hideControlsOverlay();
        else showControlsOverlay();
    }

    /**
     * Handler for long-tapping the screen
     */
    private void onLongTap() {
        if (!Preferences.isViewerHoldToZoom()) {
            float currentScale = adapter.getAbsoluteScaleAtPosition(absImageIndex);
            ReaderBottomImageFragment.invoke(requireContext(), requireActivity().getSupportFragmentManager(), absImageIndex, currentScale);
        }
    }

    private void showControlsOverlay() {
        binding.controlsOverlay.getRoot().animate().alpha(1.0f).setDuration(100).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (binding != null) {
                    binding.controlsOverlay.getRoot().setVisibility(View.VISIBLE);
                    binding.viewerPagenumberText.setVisibility(View.GONE);
                }
                setSystemBarsVisible(true);
            }
        });
    }

    private void hideControlsOverlay() {
        binding.controlsOverlay.getRoot().animate().alpha(0.0f).setDuration(100).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (binding != null) {
                    binding.controlsOverlay.getRoot().setVisibility(View.INVISIBLE);
                    onUpdatePageNumDisplay();
                }
            }
        });
        setSystemBarsVisible(false);
    }

    /**
     * Display the viewer gallery
     */
    private void displayGallery() {
        hasGalleryBeenShown = true;
        viewModel.setViewerStartingIndex(absImageIndex); // Memorize the current page

        if (getParentFragmentManager().getBackStackEntryCount() > 0) { // Gallery mode (Library -> gallery -> pager)
            getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE); // Leave only the latest element in the back stack
        } else { // Pager mode (Library -> pager -> gallery -> pager)
            getParentFragmentManager().beginTransaction().replace(android.R.id.content, ReaderGalleryFragment.newInstance()).addToBackStack(null).commit();
        }
    }

    /**
     * Show / hide bottom and top Android system bars
     *
     * @param visible True if bars have to be shown; false instead
     */
    private void setSystemBarsVisible(boolean visible) {
        Activity activity = getActivity();
        if (null == activity) return;

        int uiOptions;
        Window window = activity.getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        // TODO use androidx.core 1.6.0-beta01+ & WindowCompat (see https://stackoverflow.com/questions/62643517/immersive-fullscreen-on-android-11)
        // TODO prepare to fiddle with paddings and margins : https://stackoverflow.com/questions/57293449/go-edge-to-edge-on-android-correctly-with-windowinsets
        if (visible) {
            uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            // Revert to default regarding notch area display
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }
        } else {
            uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
            // Display around the notch area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Preferences.isViewerDisplayAroundNotch()) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }

        // Defensive programming here because crash reports show that getView() sometimes is null
        // (just don't ask me why...)
        View v = getView();
        if (v != null) v.setSystemUiVisibility(uiOptions);
        window.setAttributes(params);
    }

    private void onGetMaxDimensions(Point maxDimensions) {
        adapter.setMaxDimensions(maxDimensions.x, maxDimensions.y);
    }

    private void onSlideShowSliderChosen(int sliderIndex) {
        int prefsDelay = convertSliderPositionToPrefsDelay(sliderIndex);

        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
            Preferences.setViewerSlideshowDelayVertical(prefsDelay);
        else Preferences.setViewerSlideshowDelay(prefsDelay);

        Helper.removeLabels(binding.controlsOverlay.slideshowDelaySlider);
        binding.controlsOverlay.slideshowDelaySlider.setVisibility(View.GONE);
        startSlideshow(true);
    }

    private void startSlideshow(boolean showToast) {
        // Hide UI
        hideControlsOverlay();

        // Compute slideshow delay
        int delayPref;
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
            delayPref = Preferences.getViewerSlideshowDelayVertical();
        else delayPref = Preferences.getViewerSlideshowDelay();

        float factor;

        switch (delayPref) {
            case VIEWER_SLIDESHOW_DELAY_05:
                factor = 0.5f;
                break;
            case VIEWER_SLIDESHOW_DELAY_1:
                factor = 1;
                break;
            case VIEWER_SLIDESHOW_DELAY_4:
                factor = 4;
                break;
            case VIEWER_SLIDESHOW_DELAY_8:
                factor = 8;
                break;
            case VIEWER_SLIDESHOW_DELAY_16:
                factor = 16;
                break;
            default:
                factor = 2;
        }

        if (showToast) {
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                ToastHelper.toast(R.string.slideshow_start_vertical, getResources().getStringArray(R.array.pref_viewer_slideshow_delay_entries_vertical)[convertPrefsDelayToSliderPosition(delayPref)]);
            else ToastHelper.toast(R.string.slideshow_start, factor);
        }

        scrollListener.disableScroll();

        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences)) {
            smoothScroller = new ReaderSmoothScroller(requireContext()); // Mandatory; if we don't recreate it, we can't change scrolling speed as it is cached internally
            smoothScroller.setCurrentPositionY(scrollListener.getTotalScrolledY());
            smoothScroller.setItemHeight(adapter.getDimensionsAtPosition(absImageIndex).y);
            smoothScroller.setTargetPosition(adapter.getItemCount() - 1);
            smoothScroller.setSpeed(900f / (factor / 4f));
            llm.startSmoothScroll(smoothScroller);
        } else {
            slideshowTimer = Observable.timer((long) (factor * 1000), TimeUnit.MILLISECONDS).subscribeOn(Schedulers.computation()).repeat().observeOn(AndroidSchedulers.mainThread()).subscribe(v -> nextPage());
        }
        isSlideshowActive = true;
    }

    private void stopSlideshow() {
        if (slideshowTimer != null) {
            slideshowTimer.dispose();
            slideshowTimer = null;
        } else {
            smoothScroller = new ReaderSmoothScroller(requireContext()); // Mandatory; if we don't recreate it, we can't change scrolling speed as it is cached internally
            smoothScroller.setCurrentPositionY(scrollListener.getTotalScrolledY());
            smoothScroller.setTargetPosition(Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition()));
            llm.startSmoothScroll(smoothScroller);
        }

        isSlideshowActive = false;
        scrollListener.enableScroll();
        ToastHelper.toast(R.string.slideshow_stop);
    }
}
