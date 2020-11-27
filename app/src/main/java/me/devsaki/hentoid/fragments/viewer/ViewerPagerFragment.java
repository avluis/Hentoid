package me.devsaki.hentoid.fragments.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.ImageViewerActivity;
import me.devsaki.hentoid.adapters.ImagePagerAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.ui.InputDialog;
import me.devsaki.hentoid.util.Debouncer;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.util.exception.ContentNotRemovedException;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.viewmodels.ViewModelFactory;
import me.devsaki.hentoid.views.ZoomableFrame;
import me.devsaki.hentoid.views.ZoomableRecyclerView;
import me.devsaki.hentoid.widget.OnZoneTapListener;
import me.devsaki.hentoid.widget.PageSnapWidget;
import me.devsaki.hentoid.widget.PrefetchLinearLayoutManager;
import me.devsaki.hentoid.widget.ScrollPositionListener;
import me.devsaki.hentoid.widget.VolumeKeyListener;
import timber.log.Timber;

import static androidx.core.view.ViewCompat.requireViewById;
import static java.lang.String.format;
import static me.devsaki.hentoid.util.Preferences.Constant;

// TODO : better document and/or encapsulate the difference between
//   - paper roll mode (currently used for vertical display)
//   - independent page mode (currently used for horizontal display)
public class ViewerPagerFragment extends Fragment implements ViewerBrowseModeDialogFragment.Parent, ViewerPrefsDialogFragment.Parent, ViewerDeleteDialogFragment.Parent {

    private static final String KEY_HUD_VISIBLE = "hud_visible";
    private static final String KEY_GALLERY_SHOWN = "gallery_shown";
    private static final String KEY_SLIDESHOW_ON = "slideshow_on";

    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();
    private ImagePagerAdapter adapter;
    private PrefetchLinearLayoutManager llm;
    private PageSnapWidget pageSnapWidget;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;
    private ImageViewerViewModel viewModel;
    private int imageIndex = -1; // 0-based image index
    private int highestImageIndexReached = -1; // To manage "Mark book as read after N pages" pref
    private int maxPosition; // For navigation
    private int maxPageNumber; // For display; when pages are missing, maxPosition < maxPageNumber
    private boolean hasGalleryBeenShown = false;
    private final ScrollPositionListener scrollListener = new ScrollPositionListener(this::onScrollPositionChange);
    private Disposable slideshowTimer = null;

    private Map<String, String> bookPreferences; // Preferences of current book; to feed the book prefs dialog
    private boolean isContentArchive; // True if current content is an archive

    private Debouncer<Integer> indexRefreshDebouncer;

    // Starting index management
    private boolean isComputingImageList = false;
    private int targetStartingIndex = -1;

    // == UI ==
    private TextView noImageMessage;
    private TextView pageNumberOverlay;
    private ZoomableFrame zoomFrame;
    private ZoomableRecyclerView recyclerView;
    private RecyclerView.SmoothScroller smoothScroller;

    // Controls overlay
    private View controlsOverlay;

    // Top menu items
    private MenuItem showFavoritePagesButton;
    private MenuItem shuffleButton;

    // Bottom bar controls
    private ImageView previewImage1;
    private ImageView previewImage2;
    private ImageView previewImage3;
    private SeekBar seekBar;
    private TextView pageCurrentNumber;
    private TextView pageMaxNumber;
    private View prevBookButton;
    private View nextBookButton;
    private View galleryBtn;
    private View favouritesGalleryBtn;


    @SuppressLint("NonConstantResourceId")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_viewer_pager, container, false);

        Preferences.registerPrefsChangedListener(listener);

        initPager(rootView);
        initControlsOverlay(rootView);

//        onBrowseModeChange(); will be called by onContentChanged
        onUpdateSwipeToFling();
        onUpdatePageNumDisplay();

        // Top bar controls
        Toolbar toolbar = requireViewById(rootView, R.id.viewer_pager_toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackClick());

        toolbar.setOnMenuItemClickListener(clickedMenuItem -> {
            switch (clickedMenuItem.getItemId()) {
                case R.id.action_show_favorite_pages:
                    onShowFavouriteClick();
                    break;
                case R.id.action_page_menu:
                    onPageMenuClick();
                    break;
                case R.id.action_book_settings:
                    onBookSettingsClick();
                    break;
                case R.id.action_shuffle:
                    onShuffleClick();
                    break;
                case R.id.action_slideshow:
                    startSlideshow();
                    break;
                case R.id.action_delete_book:
                    ViewerDeleteDialogFragment.invoke(this, !isContentArchive);
                    break;
                default:
                    // Nothing to do here
            }
            return true;
        });
        showFavoritePagesButton = toolbar.getMenu().findItem(R.id.action_show_favorite_pages);
        shuffleButton = toolbar.getMenu().findItem(R.id.action_shuffle);

        indexRefreshDebouncer = new Debouncer<>(requireContext(), 75, this::applyStartingIndexInternal);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewModelFactory vmFactory = new ViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), vmFactory).get(ImageViewerViewModel.class);

        viewModel.onRestoreState(savedInstanceState);

        viewModel.getContent()
                .observe(getViewLifecycleOwner(), this::onContentChanged);

        viewModel.getImages()
                .observe(getViewLifecycleOwner(), this::onImagesChanged);

        viewModel.getStartingIndex()
                .observe(getViewLifecycleOwner(), this::onStartingIndexChanged);

        viewModel.getShuffled()
                .observe(getViewLifecycleOwner(), this::onShuffleChanged);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (controlsOverlay != null)
            outState.putInt(KEY_HUD_VISIBLE, controlsOverlay.getVisibility());
        outState.putBoolean(KEY_SLIDESHOW_ON, (slideshowTimer != null));
        outState.putBoolean(KEY_GALLERY_SHOWN, hasGalleryBeenShown);
        if (viewModel != null) {
            viewModel.setStartingIndex(imageIndex); // Memorize the current page
            viewModel.onSaveState(outState);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        int hudVisibility = View.INVISIBLE; // Default state at startup
        if (savedInstanceState != null) {
            hudVisibility = savedInstanceState.getInt(KEY_HUD_VISIBLE, View.INVISIBLE);
            hasGalleryBeenShown = savedInstanceState.getBoolean(KEY_GALLERY_SHOWN, false);
            if (savedInstanceState.getBoolean(KEY_SLIDESHOW_ON, false)) startSlideshow(false);
        }
        controlsOverlay.setVisibility(hudVisibility);
    }

    @Override
    public void onStart() {
        super.onStart();

        ((ImageViewerActivity) requireActivity()).registerKeyListener(
                new VolumeKeyListener()
                        .setOnVolumeDownListener(this::previousPage)
                        .setOnVolumeUpListener(this::nextPage)
                        .setOnBackListener(this::onBackClick));
    }

    @Override
    public void onResume() {
        super.onResume();

        setSystemBarsVisible(controlsOverlay.getVisibility() == View.VISIBLE); // System bars are visible only if HUD is visible
        if (Preferences.Constant.VIEWER_BROWSE_NONE == Preferences.getViewerBrowseMode())
            ViewerBrowseModeDialogFragment.invoke(this);
        updatePageDisplay();
        updateFavouritesGalleryButtonDisplay();
    }

    // Make sure position is saved when app is closed by the user
    @Override
    public void onStop() {
        viewModel.onLeaveBook(imageIndex, highestImageIndexReached);
        if (slideshowTimer != null) slideshowTimer.dispose();
        ((ImageViewerActivity) requireActivity()).unregisterKeyListener();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(listener);
        if (adapter != null) {
            adapter.setRecyclerView(null);
            adapter.destroy();
        }
        if (recyclerView != null) recyclerView.setAdapter(null);
        recyclerView = null;
        super.onDestroy();
    }

    private void initPager(View rootView) {
        adapter = new ImagePagerAdapter(requireContext());

        zoomFrame = requireViewById(rootView, R.id.image_viewer_zoom_frame);

        recyclerView = requireViewById(rootView, R.id.image_viewer_zoom_recycler);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.setOnGetMaxDimensionsListener(this::onGetMaxDimensions);
        recyclerView.requestFocus();
        recyclerView.setOnScaleListener(scale -> {
            if (pageSnapWidget != null && LinearLayoutManager.HORIZONTAL == llm.getOrientation()) {
                if (1.0 == scale && !pageSnapWidget.isPageSnapEnabled())
                    pageSnapWidget.setPageSnapEnabled(true);
                else if (1.0 != scale && pageSnapWidget.isPageSnapEnabled())
                    pageSnapWidget.setPageSnapEnabled(false);
            }
        });
        recyclerView.setLongTapListener(ev -> false);

        OnZoneTapListener onHorizontalZoneTapListener = new OnZoneTapListener(recyclerView)
                .setOnLeftZoneTapListener(this::onLeftTap)
                .setOnRightZoneTapListener(this::onRightTap)
                .setOnMiddleZoneTapListener(this::onMiddleTap);

        OnZoneTapListener onVerticalZoneTapListener = new OnZoneTapListener(recyclerView)
                .setOnMiddleZoneTapListener(this::onMiddleTap);

        recyclerView.setTapListener(onVerticalZoneTapListener);       // For paper roll mode (vertical)
        adapter.setItemTouchListener(onHorizontalZoneTapListener);    // For independent images mode (horizontal)

        adapter.setRecyclerView(recyclerView);

        llm = new PrefetchLinearLayoutManager(getContext());
        llm.setExtraLayoutSpace(10);
        recyclerView.setLayoutManager(llm);

        pageSnapWidget = new PageSnapWidget(recyclerView);

        smoothScroller = new LinearSmoothScroller(requireContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };

        scrollListener.setOnStartOutOfBoundScrollListener(() -> {
            if (Preferences.isViewerContinuous()) previousBook();
        });
        scrollListener.setOnEndOutOfBoundScrollListener(() -> {
            if (Preferences.isViewerContinuous()) nextBook();
        });
    }

    private void initControlsOverlay(View rootView) {
        controlsOverlay = requireViewById(rootView, R.id.image_viewer_controls_overlay);

        // Page number button
        pageCurrentNumber = requireViewById(rootView, R.id.viewer_current_page_text);
        pageCurrentNumber.setOnClickListener(v -> InputDialog.invokeNumberInputDialog(requireActivity(), R.string.goto_page, this::goToPage));
        pageMaxNumber = requireViewById(rootView, R.id.viewer_max_page_text);
        noImageMessage = requireViewById(rootView, R.id.viewer_no_img_txt);
        pageNumberOverlay = requireViewById(rootView, R.id.viewer_pagenumber_text);

        // Next/previous book
        prevBookButton = requireViewById(rootView, R.id.viewer_prev_book_btn);
        prevBookButton.setOnClickListener(v -> previousBook());
        nextBookButton = requireViewById(rootView, R.id.viewer_next_book_btn);
        nextBookButton.setOnClickListener(v -> nextBook());

        // Slider and preview
        previewImage1 = requireViewById(rootView, R.id.viewer_image_preview1);
        previewImage2 = requireViewById(rootView, R.id.viewer_image_preview2);
        previewImage3 = requireViewById(rootView, R.id.viewer_image_preview3);
        seekBar = requireViewById(rootView, R.id.viewer_seekbar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                previewImage1.setVisibility(View.VISIBLE);
                previewImage2.setVisibility(View.VISIBLE);
                previewImage3.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                previewImage1.setVisibility(View.INVISIBLE);
                previewImage2.setVisibility(View.INVISIBLE);
                previewImage3.setVisibility(View.INVISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) seekToPosition(progress);
            }
        });

        // Gallery
        galleryBtn = requireViewById(rootView, R.id.viewer_gallery_btn);
        galleryBtn.setOnClickListener(v -> displayGallery(false));
        favouritesGalleryBtn = requireViewById(rootView, R.id.viewer_favourites_btn);
        favouritesGalleryBtn.setOnClickListener(v -> displayGallery(true));
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
        ViewerPrefsDialogFragment.invoke(this, bookPreferences);
    }

    /**
     * Handle click on "Shuffle" action button
     */
    private void onShuffleClick() {
        goToPage(1);
        viewModel.onShuffleClick();
    }

    /**
     * Handle click on "Show favourite pages" action button
     */
    private void onShowFavouriteClick() {
        viewModel.toggleShowFavouritePages(this::updateShowFavouriteDisplay);
    }

    /**
     * Handle click on "Page menu" action button
     */
    private void onPageMenuClick() {
        float currentScale = adapter.getScaleAtPosition(imageIndex);
        ViewerBottomSheetFragment.show(requireContext(), requireActivity().getSupportFragmentManager(), imageIndex, currentScale);
    }

    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private void onImagesChanged(List<ImageFile> images) {
        isComputingImageList = true;
        adapter.submitList(images, this::differEndCallback);

        if (images.isEmpty()) {
            setSystemBarsVisible(true);
            noImageMessage.setVisibility(View.VISIBLE);
        } else {
            noImageMessage.setVisibility(View.GONE);
        }
    }

    /**
     * Callback for the end of image list diff calculations
     * Activated when all displayed items are placed on their definitive position
     */
    private void differEndCallback() {
        maxPosition = adapter.getItemCount() - 1;
        seekBar.setMax(maxPosition);

        // Can't access the gallery when there's no page to display
        if (maxPosition > -1) galleryBtn.setVisibility(View.VISIBLE);
        else galleryBtn.setVisibility(View.GONE);

        if (targetStartingIndex > -1) applyStartingIndex(targetStartingIndex);
        else updatePageDisplay();

        isComputingImageList = false;
    }

    /**
     * Observer for changes on the book's starting image index
     *
     * @param startingIndex Book's starting image index
     */
    private void onStartingIndexChanged(Integer startingIndex) {
        if (!isComputingImageList) applyStartingIndex(startingIndex);
        else targetStartingIndex = startingIndex;
    }

    private void applyStartingIndex(int startingIndex) {
        indexRefreshDebouncer.submit(startingIndex);
        targetStartingIndex = -1;
    }

    private void applyStartingIndexInternal(int startingIndex) {
        int currentPosition = Math.max(llm.findFirstVisibleItemPosition(), llm.findFirstCompletelyVisibleItemPosition());

        // When target position is the same as current scroll index (0), scrolling is pointless
        // -> activate scroll listener manually
        if (currentPosition == startingIndex) onScrollPositionChange(startingIndex);
        else {
            if (LinearLayoutManager.HORIZONTAL == llm.getOrientation() && recyclerView != null)
                recyclerView.scrollToPosition(startingIndex);
            else
                llm.scrollToPositionWithOffset(startingIndex, 0);
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
        onBrowseModeChange(); // TODO check if this can be optimized, as images are loaded twice when a new book is loaded

        updateBookNavigation(content);
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
        if (deletePage)
            viewModel.deletePage(imageIndex, this::onDeleteError);
        else
            viewModel.deleteBook(this::onDeleteError);
    }


    /**
     * Callback for the failure of the "delete item" action
     */
    private void onDeleteError(Throwable t) {
        Timber.e(t);
        if (t instanceof ContentNotRemovedException) {
            ContentNotRemovedException e = (ContentNotRemovedException) t;
            String message = (null == e.getMessage()) ? "Content removal failed" : e.getMessage();
            Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show();
        }
    }


    /**
     * Scroll / page change listener
     *
     * @param scrollPosition New 0-based scroll position
     */
    private void onScrollPositionChange(int scrollPosition) {
        if (scrollPosition != imageIndex) {
            boolean isScrollLTR = true;
            int direction = Preferences.getContentDirection(bookPreferences);
            if (Constant.VIEWER_DIRECTION_LTR == direction && imageIndex > scrollPosition)
                isScrollLTR = false;
            else if (Constant.VIEWER_DIRECTION_RTL == direction && imageIndex < scrollPosition)
                isScrollLTR = false;
            adapter.setScrollLTR(isScrollLTR);
        }

        imageIndex = scrollPosition;
        highestImageIndexReached = Math.max(imageIndex, highestImageIndexReached);
        ImageFile currentImage = adapter.getImageAt(imageIndex);
        if (currentImage != null) viewModel.markPageAsRead(currentImage.getOrder());

        // Resets zoom if we're using horizontal (independent pages) mode
        if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences))
            adapter.resetScaleAtPosition(scrollPosition);

        updatePageDisplay();
        updateFavouritesGalleryButtonDisplay();
    }

    /**
     * Update the display of page position controls (text and bar)
     */
    private void updatePageDisplay() {
        ImageFile img = adapter.getImageAt(imageIndex);
        if (null == img) {
            Timber.w("No image at position %s", imageIndex);
            return;
        }

        String pageNum = img.getOrder() + "";
        String maxPage = maxPageNumber + "";

        pageCurrentNumber.setText(pageNum);
        pageMaxNumber.setText(maxPage);
        pageNumberOverlay.setText(format("%s / %s", pageNum, maxPage));

        seekBar.setProgress(imageIndex);
    }

    /**
     * Update the visibility of "next/previous book" buttons
     *
     * @param content Current book
     */
    private void updateBookNavigation(@Nonnull Content content) {
        if (content.isFirst()) prevBookButton.setVisibility(View.INVISIBLE);
        else prevBookButton.setVisibility(View.VISIBLE);
        if (content.isLast()) nextBookButton.setVisibility(View.INVISIBLE);
        else nextBookButton.setVisibility(View.VISIBLE);

        maxPageNumber = content.getQtyPages();
        updatePageDisplay();
    }

    /**
     * Update the display of the favourites gallery launcher
     */
    private void updateFavouritesGalleryButtonDisplay() {
        if (adapter.isFavouritePresent())
            favouritesGalleryBtn.setVisibility(View.VISIBLE);
        else favouritesGalleryBtn.setVisibility(View.INVISIBLE);
    }

    /**
     * Update the display of the "favourite page" action button
     *
     * @param showFavouritePages True if the button has to represent a favourite page; false instead
     */
    private void updateShowFavouriteDisplay(boolean showFavouritePages) {
        if (showFavouritePages) {
            showFavoritePagesButton.setIcon(R.drawable.ic_filter_favs_on);
            showFavoritePagesButton.setTitle(R.string.viewer_filter_favourite_on);
        } else {
            showFavoritePagesButton.setIcon(R.drawable.ic_filter_favs_off);
            showFavoritePagesButton.setTitle(R.string.viewer_filter_favourite_off);
        }
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
            default:
                // Other changes aren't handled here
        }
    }

    public void onBookPreferenceChanged(@NonNull final Map<String, String> newPrefs) {
        viewModel.updateContentPreferences(newPrefs);
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

    /**
     * Re-create and re-bind all Viewholders
     */
    private void onUpdateImageDisplay() {
        adapter.refreshPrefs(bookPreferences);
        recyclerView.setAdapter(null);
        recyclerView.setLayoutManager(null);
        recyclerView.getRecycledViewPool().clear();
        recyclerView.swapAdapter(adapter, false);
        recyclerView.setLayoutManager(llm);
        adapter.notifyDataSetChanged(); // NB : will re-run onBindViewHolder for all displayed pictures
    }

    private void onUpdatePageNumDisplay() {
        pageNumberOverlay.setVisibility(Preferences.isViewerDisplayPageNum() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBrowseModeChange() {
        int currentLayoutDirection;
        // LinearLayoutManager.setReverseLayout behaves _relatively_ to current Layout Direction
        // => need to know that direction before deciding how to set setReverseLayout
        if (View.LAYOUT_DIRECTION_LTR == controlsOverlay.getLayoutDirection())
            currentLayoutDirection = Preferences.Constant.VIEWER_DIRECTION_LTR;
        else currentLayoutDirection = Preferences.Constant.VIEWER_DIRECTION_RTL;
        llm.setReverseLayout(Preferences.getContentDirection(bookPreferences) != currentLayoutDirection);

        int orientation = Preferences.getContentOrientation(bookPreferences);
        llm.setOrientation(getOrientation(orientation));

        // Resets the views to switch between paper roll mode (vertical) and independent page mode (horizontal)
        recyclerView.resetScale();
        onUpdateImageDisplay();

        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == orientation) {
            zoomFrame.enable();
            recyclerView.setLongTapZoomEnabled(Preferences.isViewerHoldToZoom());
        } else {
            zoomFrame.disable();
            recyclerView.setLongTapZoomEnabled(!Preferences.isViewerHoldToZoom());
        }

        pageSnapWidget.setPageSnapEnabled(Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == orientation);
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
     * Load next page
     */
    private void nextPage() {
        if (imageIndex == maxPosition) {
            if (Preferences.isViewerContinuous()) nextBook();
            return;
        }

        if (Preferences.isViewerTapTransitions()) {
            if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences))
                recyclerView.smoothScrollToPosition(imageIndex + 1);
            else {
                smoothScroller.setTargetPosition(imageIndex + 1);
                llm.startSmoothScroll(smoothScroller);
            }
        } else {
            if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences))
                recyclerView.scrollToPosition(imageIndex + 1);
            else
                llm.scrollToPositionWithOffset(imageIndex + 1, 0);
        }
    }

    /**
     * Load previous page
     */
    private void previousPage() {
        if (0 == imageIndex) {
            if (Preferences.isViewerContinuous()) previousBook();
            return;
        }

        if (Preferences.isViewerTapTransitions())
            recyclerView.smoothScrollToPosition(imageIndex - 1);
        else
            recyclerView.scrollToPosition(imageIndex - 1);
    }

    /**
     * Load next book
     */
    private void nextBook() {
        viewModel.onLeaveBook(imageIndex, highestImageIndexReached);
        highestImageIndexReached = -1;
        viewModel.loadNextContent();
    }

    /**
     * Load previous book
     */
    private void previousBook() {
        viewModel.onLeaveBook(imageIndex, highestImageIndexReached);
        highestImageIndexReached = -1;
        viewModel.loadPreviousContent();
    }

    /**
     * Seek to the given position; update preview images if they are visible
     *
     * @param position Position to go to (0-indexed)
     */
    private void seekToPosition(int position) {
        if (View.VISIBLE == previewImage2.getVisibility()) {
            ImageFile img = adapter.getImageAt(position - 1);
            if (img != null) {
                Glide.with(previewImage1)
                        .load(Uri.parse(img.getFileUri()))
                        .apply(glideRequestOptions)
                        .into(previewImage1);
                previewImage1.setVisibility(View.VISIBLE);
            } else previewImage1.setVisibility(View.INVISIBLE);

            img = adapter.getImageAt(position);
            if (img != null)
                Glide.with(previewImage2)
                        .load(Uri.parse(img.getFileUri()))
                        .apply(glideRequestOptions)
                        .into(previewImage2);

            img = adapter.getImageAt(position + 1);
            if (img != null) {
                Glide.with(previewImage3)
                        .load(Uri.parse(img.getFileUri()))
                        .apply(glideRequestOptions)
                        .into(previewImage3);
                previewImage3.setVisibility(View.VISIBLE);
            } else previewImage3.setVisibility(View.INVISIBLE);
        }

        if (position == imageIndex + 1 || position == imageIndex - 1) {
            recyclerView.smoothScrollToPosition(position);
        } else {
            recyclerView.scrollToPosition(position);
        }
    }

    /**
     * Go to the given page number
     *
     * @param pageNum Page number to go to (1-indexed)
     */
    private void goToPage(int pageNum) {
        int position = pageNum - 1;
        if (position == imageIndex || position < 0 || position > maxPosition)
            return;
        seekToPosition(position);
    }

    /**
     * Handler for tapping on the left zone of the screen
     */
    private void onLeftTap() {
        // Stop slideshow if it is on
        if (slideshowTimer != null) {
            stopSlideshow();
            return;
        }

        // Side-tapping disabled when view is zoomed
        if (recyclerView != null && recyclerView.getScale() != 1.0) return;
        // Side-tapping disabled when disabled in preferences
        if (!Preferences.isViewerTapToTurn()) return;

        if (Preferences.Constant.VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences))
            previousPage();
        else
            nextPage();
    }

    /**
     * Handler for tapping on the right zone of the screen
     */
    private void onRightTap() {
        // Stop slideshow if it is on
        if (slideshowTimer != null) {
            stopSlideshow();
            return;
        }

        // Side-tapping disabled when view is zoomed
        if (recyclerView != null && recyclerView.getScale() != 1.0) return;
        // Side-tapping disabled when disabled in preferences
        if (!Preferences.isViewerTapToTurn()) return;

        if (Preferences.Constant.VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences))
            nextPage();
        else
            previousPage();
    }

    /**
     * Handler for tapping on the middle zone of the screen
     */
    private void onMiddleTap() {
        // Stop slideshow if it is on
        if (slideshowTimer != null) {
            stopSlideshow();
            return;
        }

        if (View.VISIBLE == controlsOverlay.getVisibility())
            hideControlsOverlay();
        else
            showControlsOverlay();
    }

    private void showControlsOverlay() {
        controlsOverlay.animate()
                .alpha(1.0f)
                .setDuration(100)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        controlsOverlay.setVisibility(View.VISIBLE);
                        setSystemBarsVisible(true);
                    }
                });
    }

    private void hideControlsOverlay() {
        controlsOverlay.animate()
                .alpha(0.0f)
                .setDuration(100)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        controlsOverlay.setVisibility(View.INVISIBLE);
                    }
                });
        setSystemBarsVisible(false);
    }

    /**
     * Display the viewer gallery
     *
     * @param filterFavourites True if only favourite pages have to be shown; false for all pages
     */
    private void displayGallery(boolean filterFavourites) {
        hasGalleryBeenShown = true;
        viewModel.setStartingIndex(imageIndex); // Memorize the current page

        if (getParentFragmentManager().getBackStackEntryCount() > 0) { // Gallery mode (Library -> gallery -> pager)
            getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE); // Leave only the latest element in the back stack
        } else { // Pager mode (Library -> pager -> gallery -> pager)
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, ViewerGalleryFragment.newInstance(filterFavourites))
                    .addToBackStack(null)
                    .commit();
        }
    }

    /**
     * Show / hide bottom and top Android system bars
     *
     * @param visible True if bars have to be shown; false instead
     */
    private void setSystemBarsVisible(boolean visible) {
        int uiOptions;
        if (visible) {
            uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        } else {
            uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
        }

        // Defensive programming here because crash reports show that getView() sometimes is null
        // (just don't ask me why...)
        View v = getView();
        if (v != null) v.setSystemUiVisibility(uiOptions);
    }

    private void onGetMaxDimensions(Point maxDimensions) {
        adapter.setMaxDimensions(maxDimensions.x, maxDimensions.y);
    }

    private void startSlideshow() {
        startSlideshow(true);
    }

    private void startSlideshow(boolean showToast) {
        // Hide UI
        hideControlsOverlay();

        // Compute slideshow delay
        int delayPref = Preferences.getViewerSlideshowDelay();
        int delayMs;

        switch (delayPref) {
            case Constant.VIEWER_SLIDESHOW_DELAY_05:
                delayMs = 500;
                break;
            case Constant.VIEWER_SLIDESHOW_DELAY_1:
                delayMs = 1000;
                break;
            case Constant.VIEWER_SLIDESHOW_DELAY_4:
                delayMs = 4 * 1000;
                break;
            case Constant.VIEWER_SLIDESHOW_DELAY_8:
                delayMs = 8 * 1000;
                break;
            case Constant.VIEWER_SLIDESHOW_DELAY_16:
                delayMs = 16 * 1000;
                break;
            default:
                delayMs = 2 * 1000;
        }

        if (showToast)
            ToastUtil.toast(String.format(Locale.ENGLISH, "Starting slideshow (delay %.1fs)", delayMs / 1000f));
        scrollListener.disableScroll();

        slideshowTimer = Observable.timer(delayMs, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .repeat()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(v -> nextPage());
    }

    private void stopSlideshow() {
        if (slideshowTimer != null) {
            slideshowTimer.dispose();
            slideshowTimer = null;
            scrollListener.enableScroll();
            ToastUtil.toast("Slideshow stopped");
        }
    }
}
