package me.devsaki.hentoid.fragments.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.arch.lifecycle.ViewModelProviders;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.adapters.ImageRecyclerAdapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import me.devsaki.hentoid.viewmodels.ImageViewerViewModel;
import me.devsaki.hentoid.views.ZoomableRecyclerView;
import me.devsaki.hentoid.widget.OnZoneTapListener;
import me.devsaki.hentoid.widget.PageSnapWidget;
import me.devsaki.hentoid.widget.PrefetchLinearLayoutManager;
import me.devsaki.hentoid.widget.ScrollPositionListener;
import me.devsaki.hentoid.widget.VolumeGestureListener;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.support.v4.view.ViewCompat.requireViewById;
import static java.lang.String.format;

public class ImagePagerFragment extends Fragment implements GoToPageDialogFragment.Parent,
        BrowseModeDialogFragment.Parent {

    private final static String KEY_HUD_VISIBLE = "hud_visible";

    private ImageRecyclerAdapter adapter;
    private PrefetchLinearLayoutManager llm;
    private PageSnapWidget pageSnapWidget;

    private ImageViewerViewModel viewModel;
    private SharedPreferences.OnSharedPreferenceChangeListener listener = this::onSharedPreferenceChanged;
    private final RequestOptions glideRequestOptions = new RequestOptions().centerInside();

    private int maxPosition;


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
    private ImageView pageBookmarkButton;
    private TextView pageBookmarkText;

    // Bottom bar controls
    private ImageView previewImage1, previewImage2, previewImage3;
    private SeekBar seekBar;
    private TextView pageCurrentNumber;
    private TextView pageMaxNumber;
    private View prevBookButton, nextBookButton;


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
        updateBookInfo();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel
                .getImages()
                .observe(this, this::onImagesChanged);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_HUD_VISIBLE, controlsOverlay.getVisibility());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        int hudVisibility = View.INVISIBLE; // Default state at startup
        if (savedInstanceState != null) {
            hudVisibility = savedInstanceState.getInt(KEY_HUD_VISIBLE, View.INVISIBLE);
        }
        controlsOverlay.setVisibility(hudVisibility);
    }

    @Override
    public void onResume() {
        super.onResume();
        setSystemBarsVisible(controlsOverlay.getVisibility() == View.VISIBLE); // System bars are visible only if HUD is visible
        if (Preferences.Constant.PREF_VIEWER_BROWSE_NONE == Preferences.getViewerBrowseMode())
            BrowseModeDialogFragment.invoke(this);
    }

    private void initPager(View rootView) {
        adapter = new ImageRecyclerAdapter();

        VolumeGestureListener volumeGestureListener = new VolumeGestureListener()
                .setOnVolumeDownListener(this::previousPage)
                .setOnVolumeUpListener(this::nextPage);

        recyclerView = requireViewById(rootView, R.id.image_viewer_recycler);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.addOnScrollListener(new ScrollPositionListener(this::onCurrentPositionChange));
        recyclerView.setOnKeyListener(volumeGestureListener);
        recyclerView.setOnScaleListener(scale -> {
            if (pageSnapWidget != null && Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == Preferences.getViewerOrientation()) {
                if (1.0 == scale && !pageSnapWidget.isPageSnapEnabled())
                    pageSnapWidget.setPageSnapEnabled(true);
                else if (1.0 != scale && pageSnapWidget.isPageSnapEnabled())
                    pageSnapWidget.setPageSnapEnabled(false);
            }
        });
        recyclerView.setLongTapListener(ev -> false);

        OnZoneTapListener onZoneTapListener = new OnZoneTapListener(recyclerView)
                .setOnLeftZoneTapListener(this::onLeftTap)
                .setOnRightZoneTapListener(this::onRightTap)
                .setOnMiddleZoneTapListener(this::onMiddleTap);
        recyclerView.setTapListener(onZoneTapListener);

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

        // More menu / Page bookmark option
        pageBookmarkButton = requireViewById(rootView, R.id.viewer_bookmark_btn);
        pageBookmarkText = requireViewById(rootView, R.id.viewer_bookmark_text);
        pageBookmarkButton.setOnClickListener(v -> onBookmarkClick());
        pageBookmarkText.setOnClickListener(v -> onBookmarkClick());


        // Book info text
        bookInfoText = requireViewById(rootView, R.id.viewer_book_info_text);

        // Page number button
        pageCurrentNumber = requireViewById(rootView, R.id.viewer_currentpage_text);
        pageCurrentNumber.setOnClickListener(v -> GoToPageDialogFragment.show(this));
        pageMaxNumber = requireViewById(rootView, R.id.viewer_maxpage_text);
        pageNumberOverlay = requireViewById(rootView, R.id.viewer_pagenumber_text);

        // Next/previous book
        prevBookButton = requireViewById(rootView, R.id.viewer_prev_book_btn);
        prevBookButton.setOnClickListener(v -> viewModel.loadPreviousContent());
        nextBookButton = requireViewById(rootView, R.id.viewer_next_book_btn);
        nextBookButton.setOnClickListener(v -> viewModel.loadNextContent());

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
        View galleryBookmarksBtn = requireViewById(rootView, R.id.viewer_bookmarks_btn);
        galleryBookmarksBtn.setOnClickListener(v -> displayGallery(true));
    }

    public boolean onBookTitleLongClick(Content content) {
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

    private void onBackClick() {
        requireActivity().onBackPressed();
    }

    private void onSettingsClick() {
        hideMoreMenu();
        ViewerPrefsDialogFragment.invoke(this);
    }

    private void onMoreClick() {
        if (View.VISIBLE == moreMenu.getVisibility()) moreMenu.setVisibility(View.INVISIBLE);
        else moreMenu.setVisibility(View.VISIBLE);
    }

    // TODO : Use a toolbar instead of all this custom stuff
    private void hideMoreMenu() {
        moreMenu.setVisibility(View.INVISIBLE);
    }

    private void onShuffleClick() {
        viewModel.setShuffleImages(!viewModel.isShuffleImages());

        if (viewModel.isShuffleImages()) {
            pageShuffleButton.setImageResource(R.drawable.ic_menu_sort_123);
            pageShuffleText.setText(R.string.viewer_order_123);
        } else {
            pageShuffleButton.setImageResource(R.drawable.ic_menu_sort_random);
            pageShuffleText.setText(R.string.viewer_order_shuffle);
        }

        hideMoreMenu();
        goToPage(1);
    }

    private void onBookmarkClick() {
        viewModel.toggleCurrentPageBookmark(this::onBookmarkSuccess);
        hideMoreMenu();
    }

    private void onBookmarkSuccess(ImageFile img) {
        // Check if the updated image is still the one displayed on screen
        ImageFile currentImage = viewModel.getImage(viewModel.getImageIndex());
        if (currentImage != null && img.getId() == currentImage.getId()) {
            updateBookmarkDisplay();
        }
    }

    private void onImagesChanged(List<ImageFile> images) {
        hideMoreMenu();
        updateBookNavigation();
        updateBookInfo();

        adapter.setImageUris(viewModel.getUrisFromImageList(images));
        onUpdateImageDisplay(); // Remove cached images

        maxPosition = images.size() - 1;
        seekBar.setMax(maxPosition);

        if (Preferences.isViewerResumeLastLeft()) {
            viewModel.setImageIndex(viewModel.getInitialPosition());
        } else {
            viewModel.setImageIndex(0);
        }
        seekBar.setProgress(viewModel.getImageIndex());
        recyclerView.scrollToPosition(viewModel.getImageIndex());

        updatePageDisplay();
    }

    // Scroll listener
    private void onCurrentPositionChange(int position) {
        viewModel.setImageIndex(position);
        seekBar.setProgress(viewModel.getImageIndex());
        hideMoreMenu();
        updatePageDisplay();
    }

    private void updatePageDisplay() {
        String pageNum = viewModel.getImageIndex() + 1 + "";
        String maxPage = maxPosition + 1 + "";

        pageCurrentNumber.setText(pageNum);
        pageMaxNumber.setText(maxPage);
        pageNumberOverlay.setText(format("%s / %s", pageNum, maxPage));
        updateBookmarkDisplay();
    }

    private void updateBookNavigation() {
        if (viewModel.isFirstContent()) prevBookButton.setVisibility(View.INVISIBLE);
        else prevBookButton.setVisibility(View.VISIBLE);
        if (viewModel.isLastContent()) nextBookButton.setVisibility(View.INVISIBLE);
        else nextBookButton.setVisibility(View.VISIBLE);
    }

    private void updateBookmarkDisplay() {
        ImageFile currentImage = viewModel.getImage(viewModel.getImageIndex());
        if (currentImage != null) {
            if (currentImage.isBookmarked()) {
                pageBookmarkButton.setImageResource(R.drawable.ic_action_bookmark_on);
                pageBookmarkText.setText(R.string.viewer_bookmark_on);
            } else {
                pageBookmarkButton.setImageResource(R.drawable.ic_action_bookmark_off);
                pageBookmarkText.setText(R.string.viewer_bookmark_off);
            }
        }
    }

    private void updateBookInfo() {
        Content content = viewModel.getCurrentContent();
        if (content != null) {
            String title = content.getTitle();
            if (!content.getAuthor().isEmpty()) title += "\nby " + content.getAuthor();
            bookInfoText.setText(title);
            bookInfoText.setOnLongClickListener(v -> onBookTitleLongClick(content));
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (View.LAYOUT_DIRECTION_LTR == controlsOverlay.getLayoutDirection())
                currentLayoutDirection = Preferences.Constant.PREF_VIEWER_DIRECTION_LTR;
            else currentLayoutDirection = Preferences.Constant.PREF_VIEWER_DIRECTION_RTL;
        } else {
            currentLayoutDirection = Preferences.Constant.PREF_VIEWER_DIRECTION_LTR; // Only possibility before JELLY_BEAN_MR1
        }
        llm.setReverseLayout(Preferences.getViewerDirection() != currentLayoutDirection);

        llm.setOrientation(getOrientation());
        pageSnapWidget.setPageSnapEnabled(Preferences.Constant.PREF_VIEWER_ORIENTATION_VERTICAL != Preferences.getViewerOrientation());
    }

    private int getOrientation() {
        if (Preferences.Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == Preferences.getViewerOrientation()) {
            return LinearLayoutManager.HORIZONTAL;
        } else {
            return LinearLayoutManager.VERTICAL;
        }
    }

    public void nextPage() {
        hideMoreMenu();
        if (viewModel.getImageIndex() == maxPosition) return;
        if (Preferences.isViewerTapTransitions())
            recyclerView.smoothScrollToPosition(viewModel.getImageIndex() + 1);
        else
            recyclerView.scrollToPosition(viewModel.getImageIndex() + 1);
    }

    public void previousPage() {
        hideMoreMenu();
        if (viewModel.getImageIndex() == 0) return;
        if (Preferences.isViewerTapTransitions())
            recyclerView.smoothScrollToPosition(viewModel.getImageIndex() - 1);
        else
            recyclerView.scrollToPosition(viewModel.getImageIndex() - 1);
    }

    private void seekToPosition(int position) {

        if (View.VISIBLE == previewImage2.getVisibility()) {
            Glide.with(previewImage1.getContext())
                    .load(viewModel.getImage(position - 1).getAbsolutePath())
                    .apply(glideRequestOptions)
                    .into(previewImage1);

            Glide.with(previewImage1.getContext())
                    .load(viewModel.getImage(position).getAbsolutePath())
                    .apply(glideRequestOptions)
                    .into(previewImage2);

            Glide.with(previewImage1.getContext())
                    .load(viewModel.getImage(position + 1).getAbsolutePath())
                    .apply(glideRequestOptions)
                    .into(previewImage3);
        }

        if (position == viewModel.getImageIndex() + 1 || position == viewModel.getImageIndex() - 1) {
            recyclerView.smoothScrollToPosition(position);
        } else {
            recyclerView.scrollToPosition(position);
        }
        hideMoreMenu();
    }

    @Override
    public void goToPage(int pageNum) {
        hideMoreMenu();
        int position = pageNum - 1;
        if (position == viewModel.getImageIndex() || position < 0 || position > maxPosition)
            return;
        seekToPosition(position);
    }

    private void onLeftTap() {
        // Side-tapping disabled when view is zoomed
        if (recyclerView.getCurrentScale() != 1.0) return;

        if (Preferences.Constant.PREF_VIEWER_DIRECTION_LTR == Preferences.getViewerDirection())
            previousPage();
        else
            nextPage();
    }

    private void onRightTap() {
        // Side-tapping disabled when view is zoomed
        if (recyclerView.getCurrentScale() != 1.0) return;

        if (Preferences.Constant.PREF_VIEWER_DIRECTION_LTR == Preferences.getViewerDirection())
            nextPage();
        else
            previousPage();
    }

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

    private void displayGallery(boolean filterBookmarks) {
        requireFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, ImageGalleryFragment.newInstance(filterBookmarks))
                .addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
                .commit();
    }

    private void setSystemBarsVisible(boolean visible) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Window window = requireActivity().getWindow();
            if (!visible) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        } else {
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
}
