package me.devsaki.hentoid.fragments.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Point
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import com.skydoves.submarine.SubmarineItem
import com.skydoves.submarine.SubmarineView
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.adapters.ImagePagerAdapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.databinding.FragmentReaderPagerBinding
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.reader.ReaderBrowseModeDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderContentBottomSheetFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderDeleteDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderImageBottomSheetFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderNavigation.Pager
import me.devsaki.hentoid.fragments.reader.ReaderPrefsDialogFragment.Companion.invoke
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_BROWSE_NONE
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_BROWSE_RTL
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_BROWSE_TTB
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_DELETE_ASK_AGAIN
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_DELETE_TARGET_PAGE
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_DIRECTION_LTR
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_DIRECTION_RTL
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_ORIENTATION_VERTICAL
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_05
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_1
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_16
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_4
import me.devsaki.hentoid.util.Preferences.Constant.VIEWER_SLIDESHOW_DELAY_8
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.coerceIn
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.getThemedColor
import me.devsaki.hentoid.util.glideOptionCenterInside
import me.devsaki.hentoid.util.removeLabels
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.util.tryShowMenuIcons
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.views.ZoomableRecyclerView
import me.devsaki.hentoid.widget.OnZoneTapListener
import me.devsaki.hentoid.widget.PageSnapWidget
import me.devsaki.hentoid.widget.PrefetchLinearLayoutManager
import me.devsaki.hentoid.widget.ReaderKeyListener
import me.devsaki.hentoid.widget.ReaderSmoothScroller
import me.devsaki.hentoid.widget.ScrollPositionListener
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.time.Instant
import java.util.Timer
import kotlin.concurrent.timer


class ReaderPagerFragment : Fragment(R.layout.fragment_reader_pager),
    ReaderBrowseModeDialogFragment.Parent, ReaderPrefsDialogFragment.Parent,
    ReaderDeleteDialogFragment.Parent, Pager {

    private lateinit var adapter: ImagePagerAdapter
    private lateinit var llm: PrefetchLinearLayoutManager
    private lateinit var pageSnapWidget: PageSnapWidget
    private val prefsListener =
        OnSharedPreferenceChangeListener { _, key -> onSharedPreferenceChanged(key) }
    private lateinit var viewModel: ReaderViewModel
    private var absImageIndex = -1 // Absolute (book scale) 0-based image index

    private var hasGalleryBeenShown = false
    private val scrollListener = ScrollPositionListener { scrollPosition: Int ->
        onScrollPositionChange(scrollPosition)
    }

    // Slideshow
    private var slideshowTimer: Timer? = null
    private var isSlideshowActive = false
    private var slideshowPeriodMs: Long = -1
    private var latestSlideshowTick: Long = -1

    private var displayParams: DisplayParams? = null
    private var isImageAnimated = false

    // Properties
    // Preferences of current book; to feed the book prefs dialog
    private var bookPreferences: Map<String, String>? = null

    // True if current content is an archive
    private var isContentArchive = false

    // True if current content is dynamic
    private var isContentDynamic = false

    // True if current page is favourited
    private var isPageFavourite = false

    // True if current content is favourited
    private var isContentFavourite = false

    private lateinit var indexRefreshDebouncer: Debouncer<Int>
    private lateinit var processPositionDebouncer: Debouncer<Pair<Int, Int>>
    private lateinit var rescaleDebouncer: Debouncer<Float>
    private lateinit var adapterRescaleDebouncer: Debouncer<Float>
    private lateinit var zoomLevelDebouncer: Debouncer<Unit>
    private var firstZoom = true

    // Starting index management
    private var isComputingImageList = false
    private var targetStartingIndex = -1
    private var startingIndexLoaded = false
    private var contentId: Long = -1

    // == UI ==
    private var binding: FragmentReaderPagerBinding? = null
    private lateinit var smoothScroller: ReaderSmoothScroller

    // Top menu items
    private lateinit var deleteMenu: MenuItem
    private lateinit var showFavoritePagesMenu: MenuItem
    private lateinit var shuffleMenu: MenuItem
    private lateinit var reverseMenu: MenuItem

    private lateinit var navigator: ReaderNavigation

    // Debouncer for the slideshow slider
    private lateinit var slideshowSliderDebouncer: Debouncer<Int>


    // Pager implementation
    override val currentImg: ImageFile?
        get() {
            val img = adapter.getImageAt(absImageIndex)
            if (null == img) Timber.w("No image at absolute position %s", absImageIndex)
            return img
        }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        val vmFactory = ViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), vmFactory)[ReaderViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        indexRefreshDebouncer = Debouncer(lifecycleScope, 75) { startingIndex ->
            applyStartingIndexInternal(startingIndex)
        }
        slideshowSliderDebouncer = Debouncer(lifecycleScope, 2500) { sliderIndex ->
            onSlideShowSliderChosen(sliderIndex)
        }
        processPositionDebouncer = Debouncer(lifecycleScope, 75) { pair ->
            onPageChanged(pair.first, pair.second)
        }
        rescaleDebouncer = Debouncer(lifecycleScope, 100) { scale ->
            adapter.multiplyScale(scale)
            if (!firstZoom) displayZoomLevel(scale)
            else firstZoom = false
        }
        adapterRescaleDebouncer = Debouncer(lifecycleScope, 100) { scale ->
            displayZoomLevel(scale)
        }
        zoomLevelDebouncer = Debouncer(lifecycleScope, 500) {
            hideZoomLevel()
        }
        Preferences.registerPrefsChangedListener(prefsListener)
        Settings.registerPrefsChangedListener(prefsListener)
    }

    @SuppressLint("NonConstantResourceId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentReaderPagerBinding.inflate(inflater, container, false)

        displayParams = null

        initPager()
        initControlsOverlay()
        onUpdateSwipeToFling()
        onUpdatePageNumDisplay()
        onUpdateSwipeToTurn()

        // Top bar controls
        binding?.let {
            tryShowMenuIcons(
                requireActivity(), it.controlsOverlay.viewerPagerToolbar.menu
            )
            it.controlsOverlay.viewerPagerToolbar.setNavigationOnClickListener { onBackClick() }
            it.controlsOverlay.viewerPagerToolbar.setOnMenuItemClickListener { clickedMenuItem ->
                when (clickedMenuItem.itemId) {
                    R.id.action_show_favorite_pages -> onShowFavouriteClick()
                    R.id.action_book_settings -> onBookSettingsClick()
                    R.id.action_shuffle -> onShuffleClick()
                    R.id.action_reverse -> onReverseClick()
                    R.id.action_slideshow -> onSlideshowClick()
                    R.id.action_delete_book -> if (VIEWER_DELETE_ASK_AGAIN == Preferences.getReaderDeleteAskMode()) invoke(
                        this, !isContentArchive
                    )
                    else  // We already know what to delete
                        onDeleteElement(VIEWER_DELETE_TARGET_PAGE == Preferences.getReaderDeleteTarget())

                    else -> {}
                }
                true
            }
            deleteMenu =
                it.controlsOverlay.viewerPagerToolbar.menu.findItem(R.id.action_delete_book)
            showFavoritePagesMenu =
                it.controlsOverlay.viewerPagerToolbar.menu.findItem(R.id.action_show_favorite_pages)
            shuffleMenu = it.controlsOverlay.viewerPagerToolbar.menu.findItem(R.id.action_shuffle)
            reverseMenu = it.controlsOverlay.viewerPagerToolbar.menu.findItem(R.id.action_reverse)
        }
        return binding!!.root
    }

    override fun onDestroyView() {
        indexRefreshDebouncer.clear()
        slideshowSliderDebouncer.clear()
        processPositionDebouncer.clear()
        rescaleDebouncer.clear()
        adapterRescaleDebouncer.clear()
        zoomLevelDebouncer.clear()
        navigator.clear()
        binding?.recyclerView?.adapter = null
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.apply {
            getContent().observe(viewLifecycleOwner) { onContentChanged(it) }
            getViewerImages().observe(viewLifecycleOwner) { onImagesChanged(it) }
            getStartingIndex().observe(viewLifecycleOwner) { onStartingIndexChanged(it) }
            getShuffled().observe(viewLifecycleOwner) { onShuffleChanged(it) }
            getShowFavouritesOnly().observe(viewLifecycleOwner) { updateShowFavouriteDisplay(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding?.apply {
            outState.putInt(KEY_HUD_VISIBLE, controlsOverlay.root.visibility)
        }
        outState.putBoolean(KEY_SLIDESHOW_ON, isSlideshowActive)
        val currentSlideshowSeconds = Instant.now().toEpochMilli() - latestSlideshowTick
        outState.putLong(KEY_SLIDESHOW_REMAINING_MS, slideshowPeriodMs - currentSlideshowSeconds)
        outState.putBoolean(KEY_GALLERY_SHOWN, hasGalleryBeenShown)
        // Memorize current page
        outState.putInt(KEY_IMG_INDEX, absImageIndex)
        viewModel.setViewerStartingIndex(absImageIndex)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        var hudVisibility = View.INVISIBLE // Default state at startup
        if (savedInstanceState != null) {
            hudVisibility = savedInstanceState.getInt(KEY_HUD_VISIBLE, View.INVISIBLE)
            hasGalleryBeenShown = savedInstanceState.getBoolean(KEY_GALLERY_SHOWN, false)
            absImageIndex = savedInstanceState.getInt(KEY_IMG_INDEX, -1)
            if (savedInstanceState.getBoolean(KEY_SLIDESHOW_ON, false)) {
                startSlideshow(false, savedInstanceState.getLong(KEY_SLIDESHOW_REMAINING_MS))
            }
        }
        binding?.apply {
            controlsOverlay.root.visibility = hudVisibility
        }
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        (requireActivity() as ReaderActivity)
            .registerKeyListener(ReaderKeyListener(lifecycleScope)
                .setOnVolumeDownListener { b: Boolean -> if (b && Preferences.isReaderVolumeToSwitchBooks()) navigator.previousFunctional() else previousPage() }
                .setOnVolumeUpListener { b: Boolean -> if (b && Preferences.isReaderVolumeToSwitchBooks()) navigator.nextFunctional() else nextPage() }
                .setOnKeyLeftListener { onLeftTap() }.setOnKeyRightListener { onRightTap() }
                .setOnBackListener { onBackClick() })
    }

    override fun onResume() {
        super.onResume()
        binding?.apply {
            // System bars are visible only if HUD is visible
            setSystemBarsVisible(controlsOverlay.root.visibility == View.VISIBLE)
        }
        if (VIEWER_BROWSE_NONE == Preferences.getReaderBrowseMode()) invoke(this)
        navigator.updatePageControls()
    }

    // Make sure position is saved when app is closed by the user
    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        viewModel.onLeaveBook(absImageIndex)
        slideshowTimer?.cancel()
        (requireActivity() as ReaderActivity).unregisterKeyListener()
        super.onStop()
    }

    override fun onDestroy() {
        Preferences.unregisterPrefsChangedListener(prefsListener)
        Settings.unregisterPrefsChangedListener(prefsListener)
        if (this::adapter.isInitialized) {
            adapter.setRecyclerView(null)
            adapter.destroy()
        }
        super.onDestroy()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProcessEvent(event: ProcessEvent) {
        if (null == binding) return
        if (event.processId != R.id.viewer_load && event.processId != R.id.viewer_page_download) return
        if (event.processId == R.id.viewer_page_download && event.step != absImageIndex) return
        processEvent(event)
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onProcessStickyEvent(event: ProcessEvent) {
        if (null == binding) return
        if (event.processId != R.id.viewer_load && event.processId != R.id.viewer_page_download) return
        if (event.processId == R.id.viewer_page_download && event.step != absImageIndex) return
        EventBus.getDefault().removeStickyEvent(event)
        processEvent(event)
    }

    private fun processEvent(event: ProcessEvent) {
        binding?.apply {
            if (ProcessEvent.Type.PROGRESS == event.eventType) {
                @StringRes var msgResource: Int = R.string.loading_image
                if (event.processId == R.id.viewer_load) { // Archive unpacking
                    msgResource = R.string.loading_archive
                }
                viewerFixBtn.visibility = View.GONE
                viewerLoadingTxt.text = resources.getString(
                    msgResource, event.elementsKO + event.elementsOK, event.elementsTotal
                )
                // Just show it for the first iterations to allow the UI to hide it when it wants
                if (event.elementsOK + event.elementsKO < 5) viewerLoadingTxt.visibility =
                    View.VISIBLE
                progressBar.max = event.elementsTotal
                controlsOverlay.progressBar.max = event.elementsTotal
                progressBar.progress = event.elementsKO + event.elementsOK
                controlsOverlay.progressBar.progress = event.elementsKO + event.elementsOK
                progressBar.visibility = View.VISIBLE
                controlsOverlay.progressBar.visibility = View.VISIBLE
            } else if (ProcessEvent.Type.COMPLETE == event.eventType) {
                viewerLoadingTxt.visibility = View.GONE
                progressBar.visibility = View.GONE
                controlsOverlay.progressBar.visibility = View.GONE
            } else if (ProcessEvent.Type.FAILURE == event.eventType) {
                viewerLoadingTxt.visibility = View.GONE
                progressBar.visibility = View.GONE
                controlsOverlay.progressBar.visibility = View.GONE
                viewerFixBtn.visibility = View.VISIBLE
            }
        }
    }

    private fun initPager() {
        adapter = ImagePagerAdapter(requireContext())
        binding?.apply {
            recyclerView.adapter = adapter
            recyclerView.setHasFixedSize(true)
            recyclerView.addOnScrollListener(scrollListener)
            recyclerView.setOnGetMaxDimensionsListener { maxDimensions: Point ->
                onGetMaxDimensions(
                    maxDimensions
                )
            }
            recyclerView.requestFocus()
            // Scale listener from the ImageView, incl. top to bottom browsing
            recyclerView.setOnScaleListener { scale ->
                if (LinearLayoutManager.HORIZONTAL == llm.orientation) {
                    pageSnapWidget.apply {
                        if (scale - 1.0 < 0.05 && !isPageSnapEnabled()) setPageSnapEnabled(true)
                        else if (scale - 1.0 > 0.05 && isPageSnapEnabled()) setPageSnapEnabled(false)
                    }
                }
                if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                    rescaleDebouncer.submit(scale.toFloat())
            }
            recyclerView.setLongTapListener(object : ZoomableRecyclerView.LongTapListener {
                override fun onListen(ev: MotionEvent?): Boolean {
                    onLongTap()
                    return false
                }
            })
            // Scale listener from the SSIV
            adapter.setOnScaleListener { position, scale ->
                if (position == absImageIndex) adapterRescaleDebouncer.submit(scale)
            }
            adapter.setAnimationAlertListener {
                isImageAnimated = true
                displayParams?.apply {
                    if (!hasAnimation) {
                        onDisplayParamsChange(
                            DisplayParams(browseMode, displayMode, isSmoothRendering, true)
                        )
                    }
                }
            }

            adapter.setRecyclerView(recyclerView)
            llm = PrefetchLinearLayoutManager(requireContext())
            llm.orientation = LinearLayoutManager.HORIZONTAL
            llm.setExtraLayoutSpace(10)
            recyclerView.layoutManager = llm
            pageSnapWidget = PageSnapWidget(recyclerView)
        }
        smoothScroller = ReaderSmoothScroller(requireContext())
        scrollListener.setOnStartOutOfBoundScrollListener { if (Preferences.isReaderContinuous()) navigator.previousFunctional() }
        scrollListener.setOnEndOutOfBoundScrollListener { if (Preferences.isReaderContinuous()) navigator.nextFunctional() }
    }

    private fun initControlsOverlay() {
        binding?.let {
            // Slideshow slider
            val slider: Slider = it.controlsOverlay.slideshowDelaySlider
            slider.valueFrom = 0f
            val sliderValue: Int =
                if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(
                        bookPreferences
                    )
                ) convertPrefsDelayToSliderPosition(
                    Preferences.getReaderSlideshowDelayVertical()
                ) else convertPrefsDelayToSliderPosition(
                    Preferences.getReaderSlideshowDelay()
                )
            var nbEntries =
                resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries).size
            nbEntries = 1.coerceAtLeast(nbEntries - 1)

            // TODO at some point we'd need to better synch images and book loading to avoid that
            slider.value = coerceIn(sliderValue.toFloat(), 0f, nbEntries.toFloat())
            slider.valueTo = nbEntries.toFloat()
            slider.setLabelFormatter { value: Float ->
                val entries: Array<String> =
                    if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(
                            bookPreferences
                        )
                    ) {
                        resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries_vertical)
                    } else {
                        resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries)
                    }
                entries[value.toInt()]
            }
            slider.onFocusChangeListener = OnFocusChangeListener { _: View?, hasFocus: Boolean ->
                if (!hasFocus) slider.visibility = View.GONE
            }
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    slideshowSliderDebouncer.clear()
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    onSlideShowSliderChosen(slider.value.toInt())
                }
            })

            // Fix page button
            it.viewerFixBtn.setOnClickListener { fixPage() }

            // Redownload from scratch button
            it.viewerRedownloadBtn.setOnClickListener { _ ->
                viewModel.redownloadImages { _ ->
                    Snackbar.make(
                        it.recyclerView,
                        R.string.redownloaded_error,
                        BaseTransientBottomBar.LENGTH_LONG
                    ).show()
                }
            }

            // Bottom navigation controls
            navigator = ReaderNavigation(this, it)

            // Information micro menu
            it.controlsOverlay.apply {
                informationMicroMenu.setSubmarineItemClickListener { p, _ ->
                    onInfoMicroMenuClick(p)
                }
                informationMicroMenu.addSubmarineItem(
                    SubmarineItem(ContextCompat.getDrawable(requireContext(), R.drawable.ic_book))
                )
                informationMicroMenu.addSubmarineItem(
                    SubmarineItem(ContextCompat.getDrawable(requireContext(), R.drawable.ic_page))
                )
                infoBtn.setOnClickListener {
                    favouriteMicroMenu.dips()
                    informationMicroMenu.floats()
                }
                informationMicroMenu.setSubmarineCircleClickListener(favouriteMicroMenu::dips)

                // Favourite micro menu
                updateFavouriteButtonIcon()
                favouriteMicroMenu.setSubmarineItemClickListener { p, _ ->
                    onFavouriteMicroMenuClick(p)
                }
                favouriteActionBtn.setOnClickListener { onFavouriteMicroMenuOpen() }
                favouriteMicroMenu.setSubmarineCircleClickListener(favouriteMicroMenu::dips)

                // Gallery
                galleryBtn.setOnClickListener { displayGallery() }

                // Fullscreen switch
                ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                    v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                    v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
                    insets
                }
            }
        }
    }

    private fun convertPrefsDelayToSliderPosition(prefsDelay: Int): Int {
        val prefsValues = resources.getStringArray(R.array.pref_viewer_slideshow_delay_values)
            .map { s -> s.toInt() }
        for (i in prefsValues.indices) if (prefsValues[i] == prefsDelay) return i
        return 0
    }

    private fun convertSliderPositionToPrefsDelay(sliderPosition: Int): Int {
        val prefsValues = resources.getStringArray(R.array.pref_viewer_slideshow_delay_values)
            .map { s -> s.toInt() }
        return prefsValues[sliderPosition]
    }

    /**
     * Back button handler
     */
    private fun onBackClick() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    /**
     * Show the book viewer settings dialog
     */
    private fun onBookSettingsClick() {
        currentImg?.let {
            it.content.reach(it)?.apply {
                invoke(this@ReaderPagerFragment, bookPreferences)
            }
        }
    }

    /**
     * Handle click on "Shuffle" action button
     */
    private fun onShuffleClick() {
        goToPage(1)
        viewModel.toggleShuffle()
    }

    /**
     * Handle click on "Reverse" action button
     */
    private fun onReverseClick() {
        seekToIndex(adapter.itemCount - 1)
        viewModel.reverse()
    }

    /**
     * Handle click on "Slideshow" action button
     */
    private fun onSlideshowClick() {
        var startIndex =
            if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                Preferences.getReaderSlideshowDelayVertical()
            else Preferences.getReaderSlideshowDelay()
        startIndex = convertPrefsDelayToSliderPosition(startIndex)
        binding?.let {
            it.controlsOverlay.slideshowDelaySlider.value = startIndex.toFloat()
            it.controlsOverlay.slideshowDelaySlider.labelBehavior =
                LabelFormatter.LABEL_FLOATING
            it.controlsOverlay.slideshowDelaySlider.visibility = View.VISIBLE
        }
        slideshowSliderDebouncer.submit(startIndex)
    }

    /**
     * Handle click on "Show favourite pages" toggle action button
     */
    private fun onShowFavouriteClick() {
        viewModel.filterFavouriteImages(!showFavoritePagesMenu.isChecked)
    }

    /**
     * Update the display of the "favourite page" action button
     *
     * @param showFavouritePages True if the button has to represent a favourite page; false instead
     */
    private fun updateShowFavouriteDisplay(showFavouritePages: Boolean) {
        showFavoritePagesMenu.isChecked = showFavouritePages
        if (showFavouritePages) {
            showFavoritePagesMenu.setIcon(R.drawable.ic_filter_fav)
            showFavoritePagesMenu.setTitle(R.string.viewer_filter_favourite_on)
        } else {
            showFavoritePagesMenu.setIcon(R.drawable.ic_filter_fav_off)
            showFavoritePagesMenu.setTitle(R.string.viewer_filter_favourite_off)
        }
    }

    /**
     * Handle click on "Information" micro menu
     */
    private fun onInfoMicroMenuClick(menuPosition: Int) {
        if (0 == menuPosition) { // Content
            adapter.getImageAt(absImageIndex)?.let {
                invoke(
                    requireContext(),
                    requireActivity().supportFragmentManager,
                    it.contentId,
                    absImageIndex,
                    isContentDynamic
                )
            }
        } else { // Image
            val currentScale = adapter.getAbsoluteScaleAtPosition(absImageIndex)
            invoke(
                requireContext(),
                requireActivity().supportFragmentManager,
                absImageIndex,
                currentScale
            )
        }
        binding?.controlsOverlay?.informationMicroMenu?.dips()
    }

    private fun onFavouriteMicroMenuOpen() {
        binding?.apply {
            controlsOverlay.informationMicroMenu.dips()
            val favMenu: SubmarineView = controlsOverlay.favouriteMicroMenu
            favMenu.clearAllSubmarineItems()
            favMenu.addSubmarineItem(
                SubmarineItem(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (isContentFavourite) R.drawable.ic_book_fav else R.drawable.ic_book
                    )
                )
            )
            favMenu.addSubmarineItem(
                SubmarineItem(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (isPageFavourite) R.drawable.ic_page_fav else R.drawable.ic_page
                    )
                )
            )
            favMenu.floats()
        }
    }

    /**
     * Handle click on one of the "Favourite" micro menu items
     */
    private fun onFavouriteMicroMenuClick(position: Int) {
        if (0 == position) viewModel.toggleContentFavourite(absImageIndex) { newState: Boolean ->
            onBookFavouriteSuccess(newState)
        } else if (1 == position) viewModel.toggleImageFavourite(absImageIndex) { newState: Boolean ->
            onPageFavouriteSuccess(newState)
        }
        binding?.controlsOverlay?.favouriteMicroMenu?.dips()
    }

    private fun updateFavouriteButtonIcon() {
        binding?.apply {
            @DrawableRes var iconRes: Int = R.drawable.ic_fav_empty
            if (isPageFavourite) {
                iconRes =
                    if (isContentFavourite) R.drawable.ic_fav_full else R.drawable.ic_fav_bottom_half
            } else if (isContentFavourite) iconRes = R.drawable.ic_fav_top_half
            controlsOverlay.favouriteActionBtn.setImageResource(iconRes)
        }
    }

    private fun hidePendingMicroMenus() {
        binding?.apply {
            controlsOverlay.informationMicroMenu.dips()
            controlsOverlay.favouriteMicroMenu.dips()
        }
    }

    private fun onPageFavouriteSuccess(newState: Boolean) {
        toast(if (newState) R.string.page_favourite_success else R.string.page_unfavourite_success)
        isPageFavourite = newState
        updateFavouriteButtonIcon()
    }

    private fun onBookFavouriteSuccess(newState: Boolean) {
        toast(if (newState) R.string.book_favourite_success else R.string.book_unfavourite_success)
        isContentFavourite = newState
        updateFavouriteButtonIcon()
    }

    /**
     * Observer for changes in the book's list of images
     *
     * @param images Book's list of images
     */
    private fun onImagesChanged(images: List<ImageFile>) {
        if (BuildConfig.DEBUG) {
            Timber.v("IMAGES CHANGED")
            images.forEach {
                if (it.fileUri.isNotEmpty()) Timber.v("[%d] %s", it.order, it.fileUri)
            }
        }

        isComputingImageList = true
        binding?.apply {
            adapter.reset()
            // Required to communicate tapListener to the Adapter before images are binded
            images.firstOrNull()?.let {
                it.content.reach(it)?.apply {
                    adjustDisplay(bookPreferences)
                }
            }
            adapter.submitList(images) { differEndCallback() }
            if (images.isEmpty()) {
                setSystemBarsVisible(true)
                viewerNoImgTxt.visibility = View.VISIBLE
            } else {
                viewerNoImgTxt.visibility = View.GONE
                viewerLoadingTxt.visibility = View.GONE
                if (absImageIndex > -1 && absImageIndex < images.size) {
                    isPageFavourite = images[absImageIndex].favourite
                    updateFavouriteButtonIcon()
                }
            }
        }
    }

    /**
     * Callback for the end of image list diff calculations
     * Activated when all displayed items are placed on their definitive position
     */
    private fun differEndCallback() {
        if (null == binding) return
        navigator.onImagesChanged(adapter.currentList)
        if (targetStartingIndex > -1) applyStartingIndex(targetStartingIndex) else navigator.updatePageControls()
        viewModel.clearForceReload()
        isComputingImageList = false
    }

    /**
     * Observer for changes on the book's starting image index
     *
     * @param startingIndex Book's starting image index
     */
    private fun onStartingIndexChanged(startingIndex: Int) {
        if (!isComputingImageList) applyStartingIndex(startingIndex) // Returning from gallery screen
        else targetStartingIndex = startingIndex // Loading a new book
    }

    private fun applyStartingIndex(absStartingIndex: Int) {
        indexRefreshDebouncer.submit(absStartingIndex)
        targetStartingIndex = -1
    }

    private fun applyStartingIndexInternal(startingIndex: Int) {
        startingIndexLoaded = true
        val currentPosition = llm.findFirstVisibleItemPosition()
            .coerceAtLeast(llm.findFirstCompletelyVisibleItemPosition())

        // When target position is the same as current scroll index (0), scrolling is pointless
        // -> activate scroll listener manually
        if (currentPosition == startingIndex) onScrollPositionChange(startingIndex)
        else {
            if (LinearLayoutManager.HORIZONTAL == llm.orientation) {
                binding?.apply {
                    recyclerView.scrollToPosition(startingIndex)
                }
            } else llm.scrollToPositionWithOffset(startingIndex, 0)
        }
    }

    /**
     * Observer for changes on the current book
     *
     * @param content Loaded book
     */
    private fun onContentChanged(content: Content?) {
        if (null == content) {
            activity?.finish()
            return
        }
        bookPreferences = content.bookPreferences
        isContentArchive = content.isArchive
        isContentDynamic = content.isDynamic
        isContentFavourite = content.favourite
        // Wait for starting index only if content actually changes
        if (content.id != contentId) startingIndexLoaded = false
        contentId = content.id
        absImageIndex = -1 // Will be updated by onStartingIndexChanged
        navigator.onContentChanged(content)
        updateFavouriteButtonIcon()
        // Reset animation presence flag
        displayParams?.apply {
            displayParams = DisplayParams(browseMode, displayMode, isSmoothRendering, false)
            // onDisplayParamsChange will be called as soon as images are loaded
        }

        showFavoritePagesMenu.isVisible = !content.isDynamic
        deleteMenu.isVisible = !content.isDynamic

        // Display "redownload images" button if folder no longer exists and is not external nor dynamic
        binding?.apply {
            viewerRedownloadBtn.isVisible =
                (!content.folderExists && !content.isDynamic && content.status != StatusContent.EXTERNAL)
        }
    }

    /**
     * Observer for changes on the shuffled state
     *
     * @param isShuffled New shuffled state
     */
    private fun onShuffleChanged(isShuffled: Boolean) {
        if (isShuffled) {
            shuffleMenu.setIcon(R.drawable.ic_menu_sort_123)
            shuffleMenu.setTitle(R.string.viewer_order_123)
        } else {
            shuffleMenu.setIcon(R.drawable.ic_menu_sort_random)
            shuffleMenu.setTitle(R.string.viewer_order_shuffle)
        }
    }

    override fun onDeleteElement(onDeletePage: Boolean) {
        if (onDeletePage) viewModel.deletePage(absImageIndex) { t -> onDeleteError(t) }
        else viewModel.deleteContent { t -> onDeleteError(t) }
    }


    /**
     * Callback for the failure of the "delete item" action
     */
    private fun onDeleteError(t: Throwable) {
        Timber.e(t)
        if (t is ContentNotProcessedException) {
            val message =
                if (null == t.message) getString(R.string.content_removal_failed) else t.message!!
            binding?.apply {
                Snackbar.make(recyclerView, message, BaseTransientBottomBar.LENGTH_LONG).show()
            }
        }
    }


    /**
     * Scroll / page change listener
     *
     * @param scrollPosition New 0-based scroll position
     */
    private fun onScrollPositionChange(scrollPosition: Int) {
        if (null == binding) return
        if (!startingIndexLoaded) return

        if (scrollPosition != absImageIndex) {
            var isScrollLTR = true
            val direction = Preferences.getContentDirection(bookPreferences)
            if (VIEWER_DIRECTION_LTR == direction && absImageIndex > scrollPosition)
                isScrollLTR = false
            else if (VIEWER_DIRECTION_RTL == direction && absImageIndex < scrollPosition)
                isScrollLTR = false
            adapter.setScrollLTR(isScrollLTR)
            hidePendingMicroMenus()

            // Manage scaling reset / stability if we're using horizontal (independent pages) mode
            if (VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(bookPreferences)) {
                if (Preferences.isReaderMaintainHorizontalZoom() && absImageIndex > -1) {
                    val previousScale = adapter.getRelativeScaleAtPosition(absImageIndex)
                    Timber.d(">> relative scale : %s", previousScale)
                    if (previousScale > 0) adapter.setRelativeScaleAtPosition(
                        scrollPosition, previousScale
                    )
                } else {
                    adapter.resetScaleAtPosition(scrollPosition)
                }
            }

            // Don't show loading progress from previous image
            binding?.apply {
                viewerLoadingTxt.visibility = View.GONE
                viewerFixBtn.visibility = View.GONE
            }

            val scrollDirection = scrollPosition - absImageIndex
            absImageIndex = scrollPosition
            // Remember the last relevant movement and schedule it for execution
            processPositionDebouncer.submit(Pair(absImageIndex, scrollDirection))

            adapter.getImageAt(absImageIndex)?.let {
                viewModel.markPageAsRead(it.order)
                isPageFavourite = it.favourite
                updateFavouriteButtonIcon()
                Preferences.setReaderCurrentPageNum(it.order)
            }

            navigator.updatePageControls()
            firstZoom = true
        }
    }

    private fun onPageChanged(absImageIndex: Int, scrollDirection: Int) {
        currentImg?.let {
            it.content.reach(it)?.apply {
                adjustDisplay(bookPreferences)
            }
        }
        viewModel.onPageChange(absImageIndex, scrollDirection)
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param key   Key that has been changed
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        Timber.v("Prefs change detected : %s", key)
        when (key) {
            Preferences.Key.VIEWER_BROWSE_MODE, Preferences.Key.VIEWER_HOLD_TO_ZOOM, Preferences.Key.VIEWER_CONTINUOUS -> onBrowseModeChange()
            Preferences.Key.VIEWER_KEEP_SCREEN_ON -> onUpdatePrefsScreenOn()
            Preferences.Key.VIEWER_ZOOM_TRANSITIONS, Preferences.Key.VIEWER_SEPARATING_BARS, Preferences.Key.VIEWER_AUTO_ROTATE
            -> onUpdateImageDisplay(true)

            Preferences.Key.VIEWER_IMAGE_DISPLAY, Preferences.Key.VIEWER_RENDERING, Settings.Key.READER_COLOR_DEPTH
            -> onUpdateImageDisplay(false)

            Preferences.Key.VIEWER_SWIPE_TO_FLING -> onUpdateSwipeToFling()
            Preferences.Key.VIEWER_DISPLAY_PAGENUM -> onUpdatePageNumDisplay()
            Preferences.Key.VIEWER_PAGE_TURN_SWIPE -> onUpdateSwipeToTurn()
            else -> {}
        }
    }

    override fun onBookPreferenceChanged(newPrefs: Map<String, String>) {
        viewModel.updateContentPreferences(newPrefs, absImageIndex)
        bookPreferences = newPrefs
    }

    private fun onUpdatePrefsScreenOn() {
        if (Preferences.isReaderKeepScreenOn()) requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else requireActivity().window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun onUpdateSwipeToFling() {
        val flingFactor = if (Preferences.isReaderSwipeToFling()) 75 else 0
        pageSnapWidget.setFlingSensitivity(flingFactor / 100f)
    }

    private fun onUpdateSwipeToTurn() {
        if (Preferences.isReaderSwipeToTurn()) scrollListener.enableScroll() else scrollListener.disableScroll()
    }

    /**
     * Re-bind necessary Viewholders
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun onUpdateImageDisplay(isReaderWide: Boolean = true) {
        adapter.refreshPrefs()

        if (isContentDynamic && !isReaderWide) {
            // Rebind currently displayed ViewHolder
            adapter.notifyItemChanged(absImageIndex)
        } else {
            binding?.apply {
                recyclerView.adapter = null
                recyclerView.layoutManager = null
                recyclerView.recycledViewPool.clear()
                recyclerView.swapAdapter(adapter, false)
                recyclerView.layoutManager = llm
            }
            adapter.notifyDataSetChanged() // NB : will re-run onBindViewHolder for all displayed pictures
        }
        seekToIndex(absImageIndex)
    }

    private fun onUpdatePageNumDisplay() {
        binding?.apply {
            viewerPagenumberText.visibility =
                if (Preferences.isReaderDisplayPageNum()) View.VISIBLE else View.GONE
        }
    }

    private fun adjustDisplay(bookPreferences: Map<String, String>) {
        val newDisplayParams = DisplayParams(
            Preferences.getContentBrowseMode(bookPreferences),
            Preferences.getContentDisplayMode(bookPreferences),
            Preferences.isContentSmoothRendering(bookPreferences),
            displayParams?.hasAnimation ?: false
        )
        if (null == displayParams || newDisplayParams != displayParams)
            onDisplayParamsChange(newDisplayParams)
    }

    override fun onBrowseModeChange() {
        displayParams?.let {
            onDisplayParamsChange(it)
        }
    }

    private fun onDisplayParamsChange(newDisplayParams: DisplayParams) {
        // LinearLayoutManager.setReverseLayout behaves _relatively_ to current Layout Direction
        // => need to know that direction before deciding how to set setReverseLayout
        val currentDisplayParams = displayParams

        val isOrientationChange =
            (null == currentDisplayParams || currentDisplayParams.orientation != newDisplayParams.orientation)
        val isDirectionChange =
            (null == currentDisplayParams || currentDisplayParams.direction != newDisplayParams.direction)
        val isAnimationChange =
            (null == currentDisplayParams || currentDisplayParams.hasAnimation != newDisplayParams.hasAnimation)
        displayParams = newDisplayParams

        var isZoomFrameEnabled = false

        binding?.apply {
            // Animated picture appears in horizontal mode => enable zoom frame
            if (isAnimationChange && VIEWER_ORIENTATION_HORIZONTAL == newDisplayParams.orientation) {
                if (newDisplayParams.hasAnimation || isImageAnimated) {
                    isZoomFrameEnabled = true
                }
            }

            // Orientation changes
            if (isOrientationChange) {
                llm.orientation = getOrientation(newDisplayParams.orientation)

                // Resets the views to switch between paper roll mode (vertical) and independent page mode (horizontal)
                recyclerView.resetScale()

                val tapZoneScale = if (Preferences.isReaderTapToTurn2x()) 2 else 1

                if (VIEWER_ORIENTATION_VERTICAL == newDisplayParams.orientation) {
                    // For paper roll mode (vertical)
                    val onVerticalZoneTapListener =
                        OnZoneTapListener(recyclerView, 1)
                            .setOnMiddleZoneTapListener { onMiddleTap() }
                            .setOnLongTapListener { onLongTap() }
                    adapter.setItemTouchListener(onVerticalZoneTapListener)
                    isZoomFrameEnabled = true
                    onUpdateImageDisplay(false)
                } else {
                    // For independent images mode (horizontal)
                    val onHorizontalZoneTapListener =
                        OnZoneTapListener(recyclerView, tapZoneScale)
                            .setOnLeftZoneTapListener { onLeftTap() }
                            .setOnRightZoneTapListener { onRightTap() }
                            .setOnMiddleZoneTapListener { onMiddleTap() }
                            .setOnLongTapListener { onLongTap() }
                    adapter.setItemTouchListener(onHorizontalZoneTapListener)
                    seekToIndex(absImageIndex)
                }
                pageSnapWidget.setPageSnapEnabled(VIEWER_ORIENTATION_HORIZONTAL == newDisplayParams.orientation)
            }

            zoomFrame.frameEnabled = isZoomFrameEnabled
            recyclerView.setLongTapZoomEnabled(!(Preferences.isReaderHoldToZoom() xor isZoomFrameEnabled))

            // Direction changes
            if (isDirectionChange) {
                val currentLayoutDirection: Int =
                    if (View.LAYOUT_DIRECTION_LTR == controlsOverlay.root.layoutDirection) VIEWER_DIRECTION_LTR else VIEWER_DIRECTION_RTL
                llm.reverseLayout = newDisplayParams.direction != currentLayoutDirection

                adapter.setScrollLTR(VIEWER_DIRECTION_LTR == newDisplayParams.direction)
                navigator.setDirection(newDisplayParams.direction)
                navigator.updatePageControls()
            }
        }
    }

    /**
     * Transforms current Preferences orientation into LinearLayoutManager orientation code
     *
     * @return Preferred orientation, as LinearLayoutManager orientation code
     */
    private fun getOrientation(orientation: Int): Int {
        return if (VIEWER_ORIENTATION_HORIZONTAL == orientation) {
            LinearLayoutManager.HORIZONTAL
        } else {
            LinearLayoutManager.VERTICAL
        }
    }

    /**
     * Handler for the "fix" button
     */
    private fun fixPage() {
        val powerMenuBuilder = PowerMenu.Builder(requireContext()).setWidth(
            resources.getDimensionPixelSize(R.dimen.dialog_width)
        ).setAnimation(MenuAnimation.SHOW_UP_CENTER).setMenuRadius(10f).setIsMaterial(true)
            .setLifecycleOwner(requireActivity())
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87))
            .setTextTypeface(Typeface.DEFAULT)
            .setMenuColor(requireContext().getThemedColor(R.color.window_background_light))
            .setTextSize(
                dimensAsDp(requireContext(), R.dimen.text_subtitle_1)
            ).setAutoDismiss(true)
        powerMenuBuilder.addItem(
            PowerMenuItem(
                resources.getString(R.string.viewer_reload_page),
                false,
                R.drawable.ic_action_refresh,
                null,
                null,
                0
            )
        )
        powerMenuBuilder.addItem(
            PowerMenuItem(
                resources.getString(R.string.viewer_reparse_book),
                false,
                R.drawable.ic_attribute_source,
                null,
                null,
                1
            )
        )
        val powerMenu = powerMenuBuilder.build()
        powerMenu.onMenuItemClickListener =
            OnMenuItemClickListener { _: Int, (_, _, _, _, _, tag1): PowerMenuItem ->
                if (tag1 != null) {
                    val tag = tag1 as Int
                    if (0 == tag) {
                        viewModel.onPageChange(absImageIndex, 0)
                    } else if (1 == tag) {
                        binding?.apply {
                            viewModel.reparseContent { t: Throwable? ->
                                Timber.w(t)
                                viewerLoadingTxt.text =
                                    resources.getString(R.string.redownloaded_error)
                            }
                            viewerLoadingTxt.text = resources.getString(R.string.please_wait)
                            viewerLoadingTxt.visibility = View.VISIBLE
                        }
                    }
                }
            }
        powerMenu.setIconColor(ContextCompat.getColor(requireContext(), R.color.white_opacity_87))
        binding?.apply {
            powerMenu.showAtCenter(recyclerView)
        }
    }

    /**
     * Load next page
     */
    private fun nextPage() {
        if (absImageIndex == adapter.itemCount - 1) {
            if (Preferences.isReaderContinuous()) navigator.nextFunctional()
            return
        }
        binding?.apply {
            if (Preferences.isReaderTapTransitions()) {
                if (VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(
                        bookPreferences
                    )
                ) recyclerView.smoothScrollToPosition(absImageIndex + 1) else {
                    smoothScroller.targetPosition = absImageIndex + 1
                    llm.startSmoothScroll(smoothScroller)
                }
            } else {
                if (VIEWER_ORIENTATION_HORIZONTAL == Preferences.getContentOrientation(
                        bookPreferences
                    )
                ) recyclerView.scrollToPosition(absImageIndex + 1)
                else llm.scrollToPositionWithOffset(absImageIndex + 1, 0)
            }
        }
    }

    /**
     * Load previous page
     */
    private fun previousPage() {
        if (0 == absImageIndex) {
            if (Preferences.isReaderContinuous()) navigator.previousFunctional()
            return
        }
        binding?.apply {
            if (Preferences.isReaderTapTransitions())
                recyclerView.smoothScrollToPosition(absImageIndex - 1)
            else recyclerView.scrollToPosition(absImageIndex - 1)
        }
    }

    /**
     * Seek to the given position; update preview images if they are visible
     *
     * @param absIndex Index to go to (0-indexed; books-scale)
     */
    override fun seekToIndex(absIndex: Int) {
        // Hide pending micro-menus
        hidePendingMicroMenus()
        binding?.apply {
            if (View.VISIBLE == controlsOverlay.imagePreviewCenter.visibility) {
                val previousImageView: ImageView
                val nextImageView: ImageView
                if (VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences)) {
                    previousImageView = controlsOverlay.imagePreviewLeft
                    nextImageView = controlsOverlay.imagePreviewRight
                } else {
                    previousImageView = controlsOverlay.imagePreviewRight
                    nextImageView = controlsOverlay.imagePreviewLeft
                }
                val previousImg = adapter.getImageAt(absIndex - 1)
                val currentImg = adapter.getImageAt(absIndex)
                val nextImg = adapter.getImageAt(absIndex + 1)
                if (previousImg != null) {
                    Glide.with(previousImageView).load(Uri.parse(previousImg.fileUri))
                        .apply(glideOptionCenterInside).into(previousImageView)
                    previousImageView.visibility = View.VISIBLE
                } else previousImageView.visibility = View.INVISIBLE
                if (currentImg != null) Glide.with(controlsOverlay.imagePreviewCenter)
                    .load(Uri.parse(currentImg.fileUri)).apply(glideOptionCenterInside)
                    .into(controlsOverlay.imagePreviewCenter)
                if (nextImg != null) {
                    Glide.with(nextImageView).load(Uri.parse(nextImg.fileUri))
                        .apply(glideOptionCenterInside).into(nextImageView)
                    nextImageView.visibility = View.VISIBLE
                } else nextImageView.visibility = View.INVISIBLE
            }
            if (absIndex == absImageIndex + 1 || absIndex == absImageIndex - 1) {
                recyclerView.smoothScrollToPosition(absIndex)
            } else {
                recyclerView.scrollToPosition(absIndex)
            }
        }
    }

    override fun nextBook(): Boolean {
        return viewModel.loadNextContent(absImageIndex)
    }

    override fun previousBook(): Boolean {
        return viewModel.loadPreviousContent(absImageIndex)
    }

    /**
     * Go to the given page number
     *
     * @param absPageNum Asbolute page number to go to (1-indexed; book-scale)
     */
    override fun goToPage(absPageNum: Int) {
        val index = indexFromPageNum(absPageNum)
        if (index == absImageIndex || index < 0) return
        seekToIndex(index)
    }

    override fun indexFromPageNum(pageNum: Int): Int {
        return adapter.currentList.indexOfFirst { i -> i.order == pageNum }
    }

    /**
     * Handler for tapping on the left zone of the screen
     */
    private fun onLeftTap() {
        if (null == binding) return

        // Hide pending micro-menus
        hidePendingMicroMenus()

        // Stop slideshow if it is on
        if (isSlideshowActive) {
            stopSlideshow()
            return
        }

        // Side-tapping disabled when view is zoomed
        binding?.apply {
            if (recyclerView.scale != 1f) return
        }
        // Side-tapping disabled when disabled in preferences
        if (!Preferences.isReaderTapToTurn()) return
        if (VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences)) previousPage() else nextPage()
    }

    /**
     * Handler for tapping on the right zone of the screen
     */
    private fun onRightTap() {
        if (null == binding) return

        // Hide pending micro-menus
        hidePendingMicroMenus()

        // Stop slideshow if it is on
        if (isSlideshowActive) {
            stopSlideshow()
            return
        }

        // Side-tapping disabled when view is zoomed
        binding?.apply {
            if (recyclerView.scale != 1f) return
        }
        // Side-tapping disabled when disabled in preferences
        if (!Preferences.isReaderTapToTurn()) return
        if (VIEWER_DIRECTION_LTR == Preferences.getContentDirection(bookPreferences)) nextPage() else previousPage()
    }

    /**
     * Handler for tapping on the middle zone of the screen
     */
    private fun onMiddleTap() {
        binding?.apply {
            // Hide pending micro-menus
            hidePendingMicroMenus()

            // Stop slideshow if it is on
            if (isSlideshowActive) {
                stopSlideshow()
                return
            }
            if (controlsOverlay.root.isVisible) hideControlsOverlay() else showControlsOverlay()
        }
    }

    /**
     * Handler for long-tapping the screen
     */
    private fun onLongTap() {
        if (!Preferences.isReaderHoldToZoom()) {
            val currentScale = adapter.getAbsoluteScaleAtPosition(absImageIndex)
            invoke(
                requireContext(),
                requireActivity().supportFragmentManager,
                absImageIndex,
                currentScale
            )
        }
    }

    private fun showControlsOverlay() {
        binding?.apply {
            controlsOverlay.root.animate().alpha(1.0f).setDuration(100)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        binding?.apply {
                            controlsOverlay.root.visibility = View.VISIBLE
                            viewerPagenumberText.visibility = View.GONE
                        }
                        setSystemBarsVisible(true)
                    }
                })
        }
    }

    private fun hideControlsOverlay() {
        binding?.apply {
            controlsOverlay.root.animate().alpha(0.0f).setDuration(100)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        binding?.apply {
                            controlsOverlay.root.visibility = View.INVISIBLE
                            onUpdatePageNumDisplay()
                        }
                    }
                })
        }
        setSystemBarsVisible(false)
    }

    /**
     * Display the viewer gallery
     */
    private fun displayGallery() {
        hasGalleryBeenShown = true
        viewModel.setViewerStartingIndex(absImageIndex) // Memorize the current page
        if (parentFragmentManager.backStackEntryCount > 0) { // Gallery mode (Library -> gallery -> pager)
            parentFragmentManager.popBackStack(
                null, FragmentManager.POP_BACK_STACK_INCLUSIVE
            ) // Leave only the latest element in the back stack
        } else { // Pager mode (Library -> pager -> gallery -> pager)
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, ReaderGalleryFragment()).addToBackStack(null)
                .commit()
        }
    }

    /**
     * Show / hide bottom and top Android system bars
     *
     * @param visible True if bars have to be shown; false instead
     */
    private fun setSystemBarsVisible(visible: Boolean) {
        val activity = activity ?: return
        val window = activity.window
        val params = window.attributes
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (visible) {
            binding?.apply {
                WindowInsetsControllerCompat(
                    window, controlsOverlay.root
                ).show(WindowInsetsCompat.Type.systemBars())
            }
            // Revert to default regarding notch area display
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
            window.navigationBarColor =
                ContextCompat.getColor(requireContext(), R.color.black_opacity_50)
        } else {
            binding?.apply {
                WindowInsetsControllerCompat(window, controlsOverlay.root).let { controller ->
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            // Display around the notch area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Preferences.isReaderDisplayAroundNotch()) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            window.navigationBarColor =
                ContextCompat.getColor(requireContext(), R.color.transparent)
        }

        window.attributes = params
    }

    private fun onGetMaxDimensions(maxDimensions: Point) {
        adapter.setMaxDimensions(maxDimensions.x, maxDimensions.y)
    }

    private fun onSlideShowSliderChosen(sliderIndex: Int) {
        val prefsDelay = convertSliderPositionToPrefsDelay(sliderIndex)

        if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
            Preferences.setReaderSlideshowDelayVertical(prefsDelay)
        else Preferences.setReaderSlideshowDelay(prefsDelay)

        binding?.apply {
            removeLabels(controlsOverlay.slideshowDelaySlider)
            controlsOverlay.slideshowDelaySlider.visibility = View.GONE
        }
        startSlideshow(true, -1)
    }

    private fun startSlideshow(showToast: Boolean, initialDelayMs: Long) {
        // Hide UI
        hideControlsOverlay()

        // Compute slideshow delay
        val delayPref: Int =
            if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                Preferences.getReaderSlideshowDelayVertical()
            else Preferences.getReaderSlideshowDelay()

        val factor: Float = when (delayPref) {
            VIEWER_SLIDESHOW_DELAY_05 -> 0.5f
            VIEWER_SLIDESHOW_DELAY_1 -> 1f
            VIEWER_SLIDESHOW_DELAY_4 -> 4f
            VIEWER_SLIDESHOW_DELAY_8 -> 8f
            VIEWER_SLIDESHOW_DELAY_16 -> 16f
            else -> 2f
        }
        if (showToast) {
            if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences))
                toast(
                    R.string.slideshow_start_vertical,
                    resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries_vertical)[convertPrefsDelayToSliderPosition(
                        delayPref
                    )]
                ) else toast(R.string.slideshow_start, factor)
        }
        scrollListener.disableScroll()
        if (VIEWER_ORIENTATION_VERTICAL == Preferences.getContentOrientation(bookPreferences)) {
            // Mandatory; if we don't recreate it, we can't change scrolling speed as it is cached internally
            smoothScroller = ReaderSmoothScroller(requireContext())
            smoothScroller.apply {
                setCurrentPositionY(scrollListener.totalScrolledY)
                setItemHeight(adapter.getDimensionsAtPosition(absImageIndex).y)
                targetPosition = adapter.itemCount - 1
                setSpeed(900f / (factor / 4f))
            }
            llm.startSmoothScroll(smoothScroller)
        } else {
            slideshowPeriodMs = (factor * 1000).toLong()
            val initialDelayFinal = if (initialDelayMs > -1) initialDelayMs else slideshowPeriodMs
            slideshowTimer =
                timer("slideshow-timer", false, initialDelayFinal, slideshowPeriodMs) {
                    // Timer task is not on the UI thread
                    val handler = Handler(Looper.getMainLooper())
                    handler.post { onSlideshowTick() }
                }
            latestSlideshowTick = Instant.now().toEpochMilli()
        }
        isSlideshowActive = true
    }

    private fun onSlideshowTick() {
        latestSlideshowTick = Instant.now().toEpochMilli()
        nextPage()
    }

    private fun stopSlideshow() {
        if (slideshowTimer != null) {
            slideshowTimer?.cancel()
            slideshowTimer = null
        } else {
            // Mandatory; if we don't recreate it, we can't change scrolling speed as it is cached internally
            smoothScroller = ReaderSmoothScroller(requireContext())
            smoothScroller.apply {
                setCurrentPositionY(scrollListener.totalScrolledY)
                targetPosition = llm.findFirstVisibleItemPosition()
                    .coerceAtLeast(llm.findFirstCompletelyVisibleItemPosition())
            }
            llm.startSmoothScroll(smoothScroller)
        }
        isSlideshowActive = false
        scrollListener.enableScroll()
        toast(R.string.slideshow_stop)
    }

    private fun displayZoomLevel(value: Float) {
        binding?.apply {
            viewerZoomText.text = resources.getString(R.string.percent_no_digits, value * 100.0)
            viewerZoomText.isVisible = true
        }
        zoomLevelDebouncer.submit(Unit)
    }

    private fun hideZoomLevel() {
        binding?.viewerZoomText?.isVisible = false
    }

    companion object {
        const val KEY_HUD_VISIBLE = "hud_visible"
        const val KEY_GALLERY_SHOWN = "gallery_shown"
        const val KEY_SLIDESHOW_ON = "slideshow_on"
        const val KEY_SLIDESHOW_REMAINING_MS = "slideshow_remaining_ms"
        const val KEY_IMG_INDEX = "image_index"
    }

    data class DisplayParams(
        val browseMode: Int,
        val displayMode: Int,
        val isSmoothRendering: Boolean,
        val hasAnimation: Boolean
    ) {
        val orientation =
            if (browseMode == VIEWER_BROWSE_TTB) VIEWER_ORIENTATION_VERTICAL else VIEWER_ORIENTATION_HORIZONTAL
        val direction =
            if (browseMode == VIEWER_BROWSE_RTL) VIEWER_DIRECTION_RTL else VIEWER_DIRECTION_LTR
    }
}