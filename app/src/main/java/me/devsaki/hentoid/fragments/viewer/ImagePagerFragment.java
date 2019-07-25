package me.devsaki.hentoid.fragments.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.ImagePagerAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.views.ZoomableFrame;
import me.devsaki.hentoid.views.ZoomableRecyclerView;
import me.devsaki.hentoid.widget.OnZoneTapListener;
import me.devsaki.hentoid.widget.PageSnapWidget;
import me.devsaki.hentoid.widget.PrefetchLinearLayoutManager;
import me.devsaki.hentoid.widget.ScrollPositionListener;
import me.devsaki.hentoid.widget.VolumeGestureListener;

import static android.content.Context.CLIPBOARD_SERVICE;
import static androidx.core.view.ViewCompat.requireViewById;
import static java.lang.String.format;

// TODO : better document and/or encapsulate the difference between
//   - paper roll mode (currently used for vertical display)
//   - independent page mode (currently used for horizontal display)
public class ImagePagerFragment extends Fragment implements GoToPageDialogFragment.Parent,
        BrowseModeDialogFragment.Parent {

    private static final String KEY_HUD_VISIBLE = "hud_visible";
    private static final String KEY_GALLERY_SHOWN = "gallery_shown";

    private ImagePagerAdapter adapter;
    private PrefetchLinearLayoutManager llm;
    private PageSnapWidget pageSnapWidget;
    private ZoomableFrame zoomFrame;

    private ImageViewerViewModel viewModel;
    private SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;
    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private int imageIndex = -1;
    private int maxPosition;
    private boolean hasGalleryBeenShown = false;


    // Controls
    private TextView pageNumberOverlay;
    private ZoomableRecyclerView recyclerView;

    // == CONTROLS OVERLAY ==
    private View controlsOverlay;

    // Top bar controls
    private TextView bookInfoText;
    private View moreMenu;
    private ImageView pageShuffleButton;
    private TextView pageShuffleText;
    private ImageView pageFavouriteButton;
    private TextView pageFavouriteText;

    // Bottom bar controls
    private ImageView previewImage1;
    private ImageView previewImage2;
    private ImageView previewImage3;
    private SeekBar seekBar;
    private TextView pageCurrentNumber;
    private TextView pageMaxNumber;
    private View prevBookButton;
    private View nextBookButton;
    private View favouritesGalleryBtn;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_viewer, container, false);

        Preferences.registerPrefsChangedListener(listener);
        viewModel = ViewModelProviders.of(requireActivity()).get(ImageViewerViewModel.class);

        initPager(view);
        initControlsOverlay(view);

        onBrowseModeChange();
        onUpdateFlingFactor();
        onUpdatePageNumDisplay();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.onRestoreState(savedInstanceState);

        viewModel.getImages()
                .observe(this, this::onImagesChanged);

        viewModel.getStartingIndex()
                .observe(this, this::onStartingIndexChanged);

        viewModel.getContent()
                .observe(this, this::onContentChanged);

        viewModel.setOnShuffledChangeListener(this::onShuffleChanged);

        if (Preferences.isOpenBookInGalleryMode() && !hasGalleryBeenShown) displayGallery(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_HUD_VISIBLE, controlsOverlay.getVisibility());
        outState.putBoolean(KEY_GALLERY_SHOWN, hasGalleryBeenShown);
        viewModel.setStartingIndex(imageIndex); // Memorize the current page
        viewModel.onSaveState(outState);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        int hudVisibility = View.INVISIBLE; // Default state at startup
        if (savedInstanceState != null) {
            hudVisibility = savedInstanceState.getInt(KEY_HUD_VISIBLE, View.INVISIBLE);
            hasGalleryBeenShown = savedInstanceState.getBoolean(KEY_GALLERY_SHOWN, false);
        }
        controlsOverlay.setVisibility(hudVisibility);
    }

    @Override
    public void onResume() {
        super.onResume();
        setSystemBarsVisible(controlsOverlay.getVisibility() == View.VISIBLE); // System bars are visible only if HUD is visible
        if (Preferences.Constant.PREF_VIEWER_BROWSE_NONE == Preferences.getViewerBrowseMode())
            BrowseModeDialogFragment.invoke(this);
        updatePageDisplay();
        updateFavouriteDisplay();
    }

    @Override
    public void onStop() {
        super.onStop();
        viewModel.savePosition(imageIndex);
    }


    private void initPager(View rootView) {
        adapter = new ImagePagerAdapter();

        zoomFrame = requireViewById(rootView, R.id.image_viewer_zoom_frame);

        VolumeGestureListener volumeGestureListener = new VolumeGestureListener()
                .setOnVolumeDownListener(this::previousPage)
                .setOnVolumeUpListener(this::nextPage)
                .setOnBackListener(this::onBackClick);

        recyclerView = requireViewById(rootView, R.id.image_viewer_zoom_recycler);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.addOnScrollListener(new ScrollPositionListener(this::onCurrentPositionChange));
        recyclerView.setOnKeyListener(volumeGestureListener);
        recyclerView.requestFocus();
        recyclerView.setOnScaleListener(scale -> {
            if (pageSnapWidget != null && Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == Preferences.getViewerOrientation()) {
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
        llm.setItemPrefetchEnabled(true);
        llm.setPreloadItemCount(2);
        recyclerView.setLayoutManager(llm);

        pageSnapWidget = new PageSnapWidget(recyclerView);
    }

    private void initControlsOverlay(View rootView) {
        controlsOverlay = requireViewById(rootView, R.id.image_viewer_controls_overlay);
        // Back button
        View backButton = requireViewById(rootView, R.id.viewer_back_btn);
        backButton.setOnClickListener(v -> onBackClick());

        // Settings button
        View settingsButton = requireViewById(rootView, R.id.viewer_settings_btn);
        settingsButton.setOnClickListener(v -> onSettingsClick());

        // More button & menu
        View moreButton = requireViewById(rootView, R.id.viewer_more_btn);
        moreButton.setOnClickListener(v -> onMoreClick());
        moreMenu = requireViewById(rootView, R.id.viewer_more_menu);
        moreMenu.setVisibility(View.INVISIBLE);

        // More menu / Page shuffle option
        pageShuffleButton = requireViewById(rootView, R.id.viewer_shuffle_btn);
        pageShuffleText = requireViewById(rootView, R.id.viewer_shuffle_text);
        pageShuffleButton.setOnClickListener(v -> onShuffleClick());
        pageShuffleText.setOnClickListener(v -> onShuffleClick());

        // More menu / Favourite page option
        pageFavouriteButton = requireViewById(rootView, R.id.viewer_favourite_btn);
        pageFavouriteText = requireViewById(rootView, R.id.viewer_favourite_text);
        pageFavouriteButton.setOnClickListener(v -> onFavouriteClick());
        pageFavouriteText.setOnClickListener(v -> onFavouriteClick());


        // Book info text
        bookInfoText = requireViewById(rootView, R.id.viewer_book_info_text);

        // Page number button
        pageCurrentNumber = requireViewById(rootView, R.id.viewer_currentpage_text);
        pageCurrentNumber.setOnClickListener(v -> GoToPageDialogFragment.invoke(this));
        pageMaxNumber = requireViewById(rootView, R.id.viewer_maxpage_text);
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
        View galleryBtn = requireViewById(rootView, R.id.viewer_gallery_btn);
        galleryBtn.setOnClickListener(v -> displayGallery(false));
        favouritesGalleryBtn = requireViewById(rootView, R.id.viewer_favourites_btn);
        favouritesGalleryBtn.setOnClickListener(v -> displayGallery(true));
    }

    private boolean onBookTitleLongClick(Content content) {
        ClipboardManager clipboard = (ClipboardManager) requireActivity().getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("book URL", content.getGalleryUrl());
            clipboard.setPrimaryClip(clip);
            ToastUtil.toast("Book URL copied to clipboard");
            return true;
        } else return false;
    }

    @Override
    public void onDestroy() {
        Preferences.unregisterPrefsChangedListener(listener);
        super.onDestroy();
    }

    /**
     * Back button handler
     */
    private void onBackClick() {
        viewModel.savePosition(imageIndex);
        requireActivity().onBackPressed();
    }

    /**
     * Show the viewer settings dialog
     */
    private void onSettingsClick() {
        hideMoreMenu();
        ViewerPrefsDialogFragment.invoke(this);
    }

    /**
     * Show/hide the "more" submenu when the "more" button is clicked
     * TODO : Use a toolbar instead of all this custom stuff
     */
    private void onMoreClick() {
        if (View.VISIBLE == moreMenu.getVisibility()) moreMenu.setVisibility(View.INVISIBLE);
        else moreMenu.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the "more" submenu of the top bar
     * TODO : Use a toolbar instead of all this custom stuff
     */
    private void hideMoreMenu() {
        moreMenu.setVisibility(View.INVISIBLE);
    }

    /**
     * Handle click on "Shuffle" action button
     */
    private void onShuffleClick() {
        hideMoreMenu();
        goToPage(1);
        viewModel.onShuffleClick();
    }

    /**
     * Handle click on "Favourite" action button
     */
    private void onFavouriteClick() {
        ImageFile currentImage = adapter.getImageAt(imageIndex);
        if (currentImage != null)
            viewModel.togglePageFavourite(currentImage, this::onFavouriteSuccess);
        hideMoreMenu();
    }

    /**
     * Success callback when the new favourite'd state has been successfully persisted
     *
     * @param img The favourite'd / unfavourite'd ImageFile in its new state
     */
    private void onFavouriteSuccess(ImageFile img) {
        // Check if the updated image is still the one displayed on screen
        ImageFile currentImage = adapter.getImageAt(imageIndex);
        if (currentImage != null && img.getId() == currentImage.getId()) {
            currentImage.setFavourite(img.isFavourite());
            updateFavouriteDisplay(img.isFavourite());
        }
        updateFavouritesGalleryButtonDisplay();
    }

    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private void onImagesChanged(List<ImageFile> images) {
        hideMoreMenu();

        adapter.setImages(images);
        onUpdateImageDisplay(); // Remove cached images

        maxPosition = images.size() - 1;
        seekBar.setMax(maxPosition);
        updatePageDisplay();
    }

    /**
     * Observer for changes on the book's starting image index
     *
     * @param startingIndex Book's starting image index
     */
    private void onStartingIndexChanged(Integer startingIndex) {
        recyclerView.scrollToPosition(startingIndex);
    }

    /**
     * Observer for changes on the current book
     *
     * @param content Loaded book
     */
    private void onContentChanged(Content content) {
        updateBookInfo(content);
        updateBookNavigation(content);
    }

    /**
     * Observer for changes on the shuffled state
     *
     * @param isShuffled New shuffled state
     */
    private void onShuffleChanged(boolean isShuffled) {
        if (isShuffled) {
            pageShuffleButton.setImageResource(R.drawable.ic_menu_sort_123);
            pageShuffleText.setText(R.string.viewer_order_123);
        } else {
            pageShuffleButton.setImageResource(R.drawable.ic_menu_sort_random);
            pageShuffleText.setText(R.string.viewer_order_shuffle);
        }
    }


    /**
     * Scroll listener
     *
     * @param position New position
     */
    private void onCurrentPositionChange(int position) {
        if (this.imageIndex != position) {
            this.imageIndex = position;

            // Resets zoom if we're using horizontal (independent pages) mode
            if (Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == Preferences.getViewerOrientation())
                adapter.resetPosition(position);

            seekBar.setProgress(position);
            updatePageDisplay();
            updateFavouriteDisplay();
            hideMoreMenu();
        }
    }

    /**
     * Update the display of page position controls (text and bar)
     */
    private void updatePageDisplay() {
        String pageNum = imageIndex + 1 + "";
        String maxPage = maxPosition + 1 + "";

        pageCurrentNumber.setText(pageNum);
        pageMaxNumber.setText(maxPage);
        pageNumberOverlay.setText(format("%s / %s", pageNum, maxPage));
    }

    /**
     * Update the visibility of "next/previous book" buttons
     *
     * @param content Current book
     */
    private void updateBookNavigation(Content content) {
        if (content.isFirst()) prevBookButton.setVisibility(View.INVISIBLE);
        else prevBookButton.setVisibility(View.VISIBLE);
        if (content.isLast()) nextBookButton.setVisibility(View.INVISIBLE);
        else nextBookButton.setVisibility(View.VISIBLE);
    }

    /**
     * Update the display of all favourite controls (favourite page action _and_ favourites gallery launcher)
     */
    private void updateFavouriteDisplay() {
        updateFavouritesGalleryButtonDisplay();

        ImageFile currentImage = adapter.getImageAt(imageIndex);
        if (currentImage != null)
            updateFavouriteDisplay(currentImage.isFavourite());
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
     * @param isFavourited True if the button has to represent a favourite page; false instead
     */
    private void updateFavouriteDisplay(boolean isFavourited) {
        if (isFavourited) {
            pageFavouriteButton.setImageResource(R.drawable.ic_fav_full);
            pageFavouriteText.setText(R.string.viewer_favourite_on);
        } else {
            pageFavouriteButton.setImageResource(R.drawable.ic_fav_empty);
            pageFavouriteText.setText(R.string.viewer_favourite_off);
        }
    }

    /**
     * Update the book title and author on the top bar
     *
     * @param content Current content whose information to display
     */
    private void updateBookInfo(Content content) {
        String title = content.getTitle();
        if (!content.getAuthor().isEmpty()) title += "\nby " + content.getAuthor();
        bookInfoText.setText(title);
        bookInfoText.setOnLongClickListener(v -> onBookTitleLongClick(content));
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param prefs Shared preferences object
     * @param key   Key that has been changed
     */
    private void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        switch (key) {
            case Preferences.Key.PREF_VIEWER_BROWSE_MODE:
                onBrowseModeChange();
                onUpdateImageDisplay();
                break;
            case Preferences.Key.PREF_VIEWER_KEEP_SCREEN_ON:
                onUpdatePrefsScreenOn();
                break;
            case Preferences.Key.PREF_VIEWER_IMAGE_DISPLAY:
                onUpdateImageDisplay();
                break;
            case Preferences.Key.PREF_VIEWER_FLING_FACTOR:
                onUpdateFlingFactor();
                break;
            case Preferences.Key.PREF_VIEWER_DISPLAY_PAGENUM:
                onUpdatePageNumDisplay();
                break;
            default:
                // Other changes aren't handled here
        }
    }

    private void onUpdatePrefsScreenOn() {
        if (Preferences.isViewerKeepScreenOn())
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onUpdateFlingFactor() {
        pageSnapWidget.setFlingSensitivity(Preferences.getViewerFlingFactor() / 100f);
    }

    private void onUpdateImageDisplay() {
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
            currentLayoutDirection = Preferences.Constant.PREF_VIEWER_DIRECTION_LTR;
        else currentLayoutDirection = Preferences.Constant.PREF_VIEWER_DIRECTION_RTL;
        llm.setReverseLayout(Preferences.getViewerDirection() != currentLayoutDirection);

        // Resets the views to switch between paper roll mode (vertical) and independent page mode (horizontal)
        recyclerView.resetScale();
        adapter.notifyDataSetChanged();

        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL == Preferences.getViewerOrientation())
            zoomFrame.enable();
        else zoomFrame.disable();

        llm.setOrientation(getOrientation());
        pageSnapWidget.setPageSnapEnabled(Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL != Preferences.getViewerOrientation());
    }

    /**
     * Transforms current Preferences orientation into LinearLayoutManager orientation code
     *
     * @return Preferred orientation, as LinearLayoutManager orientation code
     */
    private int getOrientation() {
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == Preferences.getViewerOrientation()) {
            return LinearLayoutManager.HORIZONTAL;
        } else {
            return LinearLayoutManager.VERTICAL;
        }
    }

    /**
     * Load next page
     */
    private void nextPage() {
        hideMoreMenu();
        if (imageIndex == maxPosition) return;
        if (Preferences.isViewerTapTransitions())
            recyclerView.smoothScrollToPosition(imageIndex + 1);
        else
            recyclerView.scrollToPosition(imageIndex + 1);
    }

    /**
     * Load previous page
     */
    private void previousPage() {
        hideMoreMenu();
        if (imageIndex == 0) return;
        if (Preferences.isViewerTapTransitions())
            recyclerView.smoothScrollToPosition(imageIndex - 1);
        else
            recyclerView.scrollToPosition(imageIndex - 1);
    }

    /**
     * Load next book
     */
    private void nextBook() {
        viewModel.savePosition(imageIndex);
        viewModel.loadNextContent();
    }

    /**
     * Load previous book
     */
    private void previousBook() {
        viewModel.savePosition(imageIndex);
        viewModel.loadPreviousContent();
    }

    /**
     * Seek to the given position; update preview images if they are visible
     *
     * @param position Position to go to (0-indexed)
     */
    private void seekToPosition(int position) {
        hideMoreMenu();
        if (View.VISIBLE == previewImage2.getVisibility()) {
            Context ctx = previewImage2.getContext().getApplicationContext();
            ImageFile img = adapter.getImageAt(position - 1);
            if (img != null) {
                Glide.with(ctx)
                        .load(img.getAbsolutePath())
                        .apply(glideRequestOptions)
                        .into(previewImage1);
                previewImage1.setVisibility(View.VISIBLE);
            } else previewImage1.setVisibility(View.INVISIBLE);

            img = adapter.getImageAt(position);
            if (img != null)
                Glide.with(ctx)
                        .load(img.getAbsolutePath())
                        .apply(glideRequestOptions)
                        .into(previewImage2);

            img = adapter.getImageAt(position + 1);
            if (img != null) {
                Glide.with(ctx)
                        .load(img.getAbsolutePath())
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
    @Override
    public void goToPage(int pageNum) {
        hideMoreMenu();
        int position = pageNum - 1;
        if (position == imageIndex || position < 0 || position > maxPosition)
            return;
        seekToPosition(position);
    }

    /**
     * Handler for tapping on the left zone of the screen
     */
    private void onLeftTap() {
        // Side-tapping disabled when view is zoomed
        if (recyclerView.getCurrentScale() != 1.0) return;

        if (Preferences.Constant.PREF_VIEWER_DIRECTION_LTR == Preferences.getViewerDirection())
            previousPage();
        else
            nextPage();
    }

    /**
     * Handler for tapping on the right zone of the screen
     */
    private void onRightTap() {
        // Side-tapping disabled when view is zoomed
        if (recyclerView.getCurrentScale() != 1.0) return;

        if (Preferences.Constant.PREF_VIEWER_DIRECTION_LTR == Preferences.getViewerDirection())
            nextPage();
        else
            previousPage();
    }

    /**
     * Handler for tapping on the middle zone of the screen
     */
    private void onMiddleTap() {
        if (View.VISIBLE == controlsOverlay.getVisibility()) {
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
        } else {
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
        hideMoreMenu();
    }

    /**
     * Display the viewer gallery
     *
     * @param filterFavourites True if only favourite pages have to be shown; false for all pages
     */
    private void displayGallery(boolean filterFavourites) {
        hasGalleryBeenShown = true;
        viewModel.setStartingIndex(imageIndex); // Memorize the current page
        requireFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, ImageGalleryFragment.newInstance(filterFavourites))
                .addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
                .commit();
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
}
