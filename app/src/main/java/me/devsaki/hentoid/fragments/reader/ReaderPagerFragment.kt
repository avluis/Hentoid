package me.devsaki.hentoid.fragments.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
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
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.OnMenuItemClickListener
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import com.skydoves.submarine.SubmarineItem
import com.skydoves.submarine.SubmarineView
import kotlinx.coroutines.launch
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.ReaderActivity
import me.devsaki.hentoid.adapters.ImagePagerAdapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.FragmentReaderPagerBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.fragments.reader.ReaderBrowseModeDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderContentBottomSheetFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderDeleteDialogFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderImageBottomSheetFragment.Companion.invoke
import me.devsaki.hentoid.fragments.reader.ReaderPrefsDialogFragment.Companion.invoke
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Key.VIEWER_AUTO_ROTATE
import me.devsaki.hentoid.util.Settings.Key.VIEWER_BROWSE_MODE
import me.devsaki.hentoid.util.Settings.Key.VIEWER_DOUBLE_TAP_TO_ZOOM
import me.devsaki.hentoid.util.Settings.Key.VIEWER_HOLD_TO_ZOOM
import me.devsaki.hentoid.util.Settings.Key.VIEWER_IMAGE_DISPLAY
import me.devsaki.hentoid.util.Settings.Key.VIEWER_RENDERING
import me.devsaki.hentoid.util.Settings.Key.VIEWER_SEPARATING_BARS
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DELETE_ASK_AGAIN
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DELETE_TARGET_PAGE
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DIRECTION_LTR
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DIRECTION_RTL
import me.devsaki.hentoid.util.Settings.Value.VIEWER_ORIENTATION_HORIZONTAL
import me.devsaki.hentoid.util.Settings.Value.VIEWER_ORIENTATION_VERTICAL
import me.devsaki.hentoid.util.dimensAsDp
import me.devsaki.hentoid.util.exception.ContentNotProcessedException
import me.devsaki.hentoid.util.getThemedColor
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


private const val KEY_HUD_VISIBLE = "hud_visible"
private const val KEY_GALLERY_SHOWN = "gallery_shown"
private const val KEY_IMG_INDEX = "image_index"

class ReaderPagerFragment : Fragment(R.layout.fragment_reader_pager),
    ReaderBrowseModeDialogFragment.Parent, ReaderPrefsDialogFragment.Parent,
    ReaderDeleteDialogFragment.Parent, ReaderNavigation.Pager, ReaderSlideshow.Pager {

    override lateinit var adapter: ImagePagerAdapter
    override lateinit var layoutMgr: LinearLayoutManager
    private lateinit var pageSnapWidget: PageSnapWidget
    private val prefsListener =
        OnSharedPreferenceChangeListener { _, key -> onSharedPreferenceChanged(key) }
    private lateinit var viewModel: ReaderViewModel

    // Absolute (book scale) 0-based image index
    override var absImageIndex = -1

    // Absolute (book scale) 0-based image index for reached position listened
    var reachedPosition = -1

    private var hasGalleryBeenShown = false
    override val scrollListener = ScrollPositionListener { onScrollPositionChange(it) }

    override var displayParams: DisplayParams? = null

    // Properties
    // Preferences of current book; to feed the book prefs dialog
    private var bookPreferences: Map<String, String>? = null
    private var bookSite: Site = Site.NONE

    // True if current content is an archive
    private var isContentArchive = false

    // True if current content is dynamic
    private var isContentDynamic = false

    // True if current page is favourited
    private var isPageFavourite = false

    // True if current content is favourited
    private var isContentFavourite = false

    // True if current content has been loaded from Folders mode
    private var isFoldersMode = false

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
    private lateinit var slideshowMgr: ReaderSlideshow

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
        Settings.registerPrefsChangedListener(prefsListener)
    }

    @SuppressLint("NonConstantResourceId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
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
                requireActivity(), it.controlsOverlay.toolbar.menu
            )
            it.controlsOverlay.toolbar.setNavigationOnClickListener { onBackClick() }
            it.controlsOverlay.toolbar.setOnMenuItemClickListener { clickedMenuItem ->
                when (clickedMenuItem.itemId) {
                    R.id.action_show_favorite_pages -> onShowFavouriteClick()
                    R.id.action_book_settings -> onBookSettingsClick()
                    R.id.action_shuffle -> onShuffleClick()
                    R.id.action_reverse -> onReverseClick()
                    R.id.action_slideshow -> onSlideshowClick()
                    R.id.action_delete_book -> if (VIEWER_DELETE_ASK_AGAIN == Settings.readerDeleteAskMode) invoke(
                        this, !isContentArchive
                    )
                    else  // We already know what to delete
                        onDeleteElement(VIEWER_DELETE_TARGET_PAGE == Settings.readerDeleteTarget)

                    else -> {}
                }
                true
            }
            deleteMenu =
                it.controlsOverlay.toolbar.menu.findItem(R.id.action_delete_book)
            showFavoritePagesMenu =
                it.controlsOverlay.toolbar.menu.findItem(R.id.action_show_favorite_pages)
            shuffleMenu = it.controlsOverlay.toolbar.menu.findItem(R.id.action_shuffle)
            reverseMenu = it.controlsOverlay.toolbar.menu.findItem(R.id.action_reverse)

            ViewCompat.setOnApplyWindowInsetsListener(it.root) { _, insets: WindowInsetsCompat ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                // Covers all landscape cases
                val navBarHeight = nav.left + nav.right + nav.top + nav.bottom
                val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())

                // Move the overlay not to be covered by navigation shapes
                val isLandscape =
                    (Configuration.ORIENTATION_LANDSCAPE == resources.configuration.orientation)
                binding?.controlsOverlay?.root?.setPadding(
                    0,
                    status.top,
                    if (isLandscape) navBarHeight else 0,
                    if (isLandscape) 0 else navBarHeight
                )
                WindowInsetsCompat.CONSUMED
            }
        }
        return binding?.root
    }

    override fun onDestroyView() {
        indexRefreshDebouncer.clear()
        processPositionDebouncer.clear()
        rescaleDebouncer.clear()
        adapterRescaleDebouncer.clear()
        zoomLevelDebouncer.clear()
        navigator.clear()
        slideshowMgr.clear()
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
        outState.putBoolean(KEY_GALLERY_SHOWN, hasGalleryBeenShown)
        // Memorize current page
        outState.putInt(KEY_IMG_INDEX, absImageIndex)
        slideshowMgr.onSaveInstanceState(outState)
        viewModel.setViewerStartingIndex(absImageIndex)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        var hudVisibility = View.INVISIBLE // Default state at startup
        savedInstanceState?.apply {
            hudVisibility = getInt(KEY_HUD_VISIBLE, View.INVISIBLE)
            hasGalleryBeenShown = getBoolean(KEY_GALLERY_SHOWN, false)
            absImageIndex = getInt(KEY_IMG_INDEX, -1)
            slideshowMgr.onViewStateRestored(this)
        }
        binding?.apply {
            controlsOverlay.root.visibility = hudVisibility
        }
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        (requireActivity() as ReaderActivity)
            .registerKeyListener(
                ReaderKeyListener(lifecycleScope)
                    .setOnVolumeDownListener { b -> if (b && Settings.isReaderVolumeToSwitchBooks) navigator.previousContainer() else previousPage() }
                    .setOnVolumeUpListener { b -> if (b && Settings.isReaderVolumeToSwitchBooks) navigator.nextContainer() else nextPage() }
                    .setOnKeyLeftListener { onLeftTap() }.setOnKeyRightListener { onRightTap() }
                    .setOnBackListener { onBackClick() })
    }

    override fun onResume() {
        super.onResume()
        binding?.apply {
            // System bars are visible only if HUD is visible
            setSystemBarsVisible(controlsOverlay.root.isVisible)
        }
        if (Settings.Value.VIEWER_BROWSE_NONE == Settings.appReaderBrowseMode) invoke(this)
        navigator.updatePageControls()
    }

    // Make sure position is saved when app is closed by the user
    override fun onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        viewModel.onLeaveBook(absImageIndex)
        slideshowMgr.cancel()
        (requireActivity() as ReaderActivity).unregisterKeyListener()
        super.onStop()
    }

    override fun onDestroy() {
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
            recyclerView.setOnGetMaxDimensionsListener { onGetMaxDimensions(it) }
            recyclerView.requestFocus()
            // Scale listener from the ImageView, incl. top to bottom browsing
            recyclerView.setOnScaleListener { onScaleChanged(it.toFloat()) }
            recyclerView.setLongTapListener(object : ZoomableRecyclerView.LongTapListener {
                override fun onListen(ev: MotionEvent?): Boolean {
                    onLongTap()
                    return false
                }
            })
            // Scale listener from the SSIV
            adapter.setOnScaleListener { position, scale ->
                if (position == absImageIndex) onScaleChanged(scale)
            }

            adapter.setRecyclerView(recyclerView)
            PrefetchLinearLayoutManager(requireContext()).let {
                it.orientation = RecyclerView.HORIZONTAL
                it.setExtraLayoutSpace(10)
                layoutMgr = it
                recyclerView.layoutManager = layoutMgr
            }
            pageSnapWidget = PageSnapWidget(recyclerView)
        }
        smoothScroller = ReaderSmoothScroller(requireContext())
        scrollListener.setOnStartOutOfBoundScrollListener { if (Settings.isReaderContinuous) navigator.previousContainer() }
        scrollListener.setOnEndOutOfBoundScrollListener { if (Settings.isReaderContinuous) navigator.nextContainer() }
        scrollListener.setOnPositionReachedListener { onScrollPositionReached(it) }
    }

    private fun initControlsOverlay() {
        binding?.let {
            // Slideshow slider
            slideshowMgr = ReaderSlideshow(this, lifecycleScope)
            slideshowMgr.init(it, this.resources)

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
                    SubmarineItem(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_book
                        )
                    )
                )
                informationMicroMenu.addSubmarineItem(
                    SubmarineItem(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_page
                        )
                    )
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
            it.linkedContent?.apply {
                invoke(this@ReaderPagerFragment, this.site, bookPreferences)
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
        slideshowMgr.showUI()
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
        binding?.controlsOverlay?.apply {
            @DrawableRes var iconRes: Int = R.drawable.ic_fav_empty
            if (isPageFavourite) {
                iconRes =
                    if (isContentFavourite) R.drawable.ic_fav_full else R.drawable.ic_fav_bottom_half
            } else if (isContentFavourite) iconRes = R.drawable.ic_fav_top_half
            favouriteActionBtn.setImageResource(iconRes)
            favouriteActionBtn.visibility = if (isFoldersMode) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun hidePendingMicroMenus() {
        binding?.controlsOverlay?.apply {
            informationMicroMenu.dips()
            favouriteMicroMenu.dips()
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
        val currentPosition = layoutMgr.findFirstVisibleItemPosition()
            .coerceAtLeast(layoutMgr.findFirstCompletelyVisibleItemPosition())

        // When target position is the same as current scroll index (0), scrolling is pointless
        // -> activate scroll listener manually
        if (currentPosition == startingIndex) onScrollPositionChange(startingIndex)
        else {
            if (RecyclerView.HORIZONTAL == layoutMgr.orientation)
                binding?.recyclerView?.scrollToPosition(startingIndex)
            else layoutMgr.scrollToPositionWithOffset(startingIndex, 0)
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
        Timber.v("Content changed")
        bookPreferences = content.bookPreferences
        bookSite = content.site
        isContentArchive = content.isArchive
        isContentDynamic = content.isDynamic
        isContentFavourite = content.favourite
        isFoldersMode = content.status == StatusContent.STORAGE_RESOURCE
        // Wait for starting index only if content actually changes
        if (content.id != contentId) {
            adjustDisplay(content.site, content.bookPreferences)
            startingIndexLoaded = false
        }
        contentId = content.id
        absImageIndex = -1 // Will be updated by onStartingIndexChanged
        reachedPosition = -1
        navigator.onContentChanged(content)
        updateFavouriteButtonIcon()

        showFavoritePagesMenu.isVisible = !isContentDynamic && !isFoldersMode
        deleteMenu.isVisible = !isContentDynamic

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
            if (VIEWER_DIRECTION_LTR == displayParams?.direction && absImageIndex > scrollPosition)
                isScrollLTR = false
            else if (VIEWER_DIRECTION_RTL == displayParams?.direction && absImageIndex < scrollPosition)
                isScrollLTR = false
            adapter.setScrollLTR(isScrollLTR)
            hidePendingMicroMenus()

            if (VIEWER_ORIENTATION_HORIZONTAL == displayParams?.orientation) {
                // Manage scaling reset / stability if we're using horizontal (independent pages) mode
                if (Settings.isReaderMaintainHorizontalZoom && absImageIndex > -1) {
                    val previousScale = adapter.getRelativeScaleAtPosition(absImageIndex)
                    Timber.d(">> relative scale : %s", previousScale)
                    if (previousScale > 0)
                        adapter.setRelativeScaleAtPosition(scrollPosition, previousScale)
                } else {
                    adapter.resetScaleAtPosition(scrollPosition)
                }
                // Reactivate snap to avoid shuffling through all pages while zoom is on
                pageSnapWidget.setPageSnapEnabled(true)
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
            }

            navigator.updatePageControls()
            firstZoom = true
        }
    }

    private fun onPageChanged(absImageIndex: Int, scrollDirection: Int) {
        currentImg?.let {
            it.linkedContent?.apply {
                adjustDisplay(this.site, bookPreferences, absImageIndex)
            }
        }
        if (VIEWER_ORIENTATION_VERTICAL == displayParams?.orientation)
            slideshowMgr.onPageChange(true)
        viewModel.onPageChange(absImageIndex, scrollDirection)
    }

    /**
     * Scroll / page reach listener
     * NB : Triggered when a page becomes visible as opposed to onScrollPositionChange
     * that is triggered when that page takes up the majority of the screen
     */
    private fun onScrollPositionReached(position: Int) {
        if (position == absImageIndex || position == reachedPosition) return
        reachedPosition = position
    }

    /**
     * Listener for preference changes (from the settings dialog)
     *
     * @param key   Key that has been changed
     */
    private fun onSharedPreferenceChanged(key: String?) {
        if (null == key) return
        Timber.v("Prefs change detected : %s", key)
        if (key.startsWith(VIEWER_BROWSE_MODE)) onBrowseModeChange()
        when (key) {
            VIEWER_HOLD_TO_ZOOM, Settings.Key.VIEWER_CONTINUOUS, Settings.Key.READER_TWOPAGES -> onBrowseModeChange()
            Settings.Key.VIEWER_KEEP_SCREEN_ON -> onUpdatePrefsScreenOn()
            Settings.Key.VIEWER_ZOOM_TRANSITIONS, VIEWER_SEPARATING_BARS, VIEWER_AUTO_ROTATE
                -> onUpdateImageDisplay(true)

            VIEWER_DOUBLE_TAP_TO_ZOOM -> {
                // Actions on both ImageView and SSIV
                onBrowseModeChange()
                onUpdateImageDisplay(true)
            }

            VIEWER_IMAGE_DISPLAY, VIEWER_RENDERING, Settings.Key.READER_COLOR_DEPTH
                -> onUpdateImageDisplay(false)

            Settings.Key.VIEWER_SWIPE_TO_FLING -> onUpdateSwipeToFling()
            Settings.Key.VIEWER_DISPLAY_PAGENUM -> onUpdatePageNumDisplay()
            Settings.Key.VIEWER_PAGE_TURN_SWIPE -> onUpdateSwipeToTurn()
            else -> {}
        }
    }

    override fun onBookPreferenceChanged(newPrefs: Map<String, String>) {
        viewModel.updateContentPreferences(newPrefs, absImageIndex)
        bookPreferences = newPrefs
    }

    private fun onUpdatePrefsScreenOn() {
        if (Settings.isReaderKeepScreenOn) requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else requireActivity().window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun onUpdateSwipeToFling() {
        val flingFactor = if (Settings.isReaderSwipeToFling) 75 else 0
        pageSnapWidget.setFlingSensitivity(flingFactor / 100f)
    }

    private fun onUpdateSwipeToTurn() {
        if (Settings.isReaderSwipeToTurn) scrollListener.enableScroll() else scrollListener.disableScroll()
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
                recyclerView.layoutManager = layoutMgr
            }
            adapter.notifyDataSetChanged() // NB : will re-run onBindViewHolder for all displayed pictures
        }
        seekToIndex(absImageIndex)
    }

    private fun onUpdatePageNumDisplay() {
        binding?.apply {
            viewerPagenumberText.visibility =
                if (Settings.isReaderDisplayPageNum) View.VISIBLE else View.GONE
        }
    }

    private fun adjustDisplay(
        site: Site,
        bookPreferences: Map<String, String>,
        absImageIndex: Int = -1
    ) {
        lifecycleScope.launch {
            val newDisplayParams = DisplayParams(
                Settings.getContentBrowseMode(site, bookPreferences),
                Settings.getContentDisplayMode(site, bookPreferences),
                Settings.getContent2PagesMode(bookPreferences),
                Settings.isContentSmoothRendering(bookPreferences),
                if (absImageIndex > -1) adapter.getSSivAtPosition(absImageIndex) else true
            )
            if (null == displayParams || newDisplayParams != displayParams)
                onDisplayParamsChange(newDisplayParams)
        }
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
        val isLayoutChange =
            (null == currentDisplayParams || currentDisplayParams.twoPages != newDisplayParams.twoPages)
        displayParams = newDisplayParams

        var isZoomFrameEnabled = false
        var shouldUpdateImageDisplay = false

        binding?.apply {
            // SSIV appears in horizontal mode => enable zoom frame
            if (VIEWER_ORIENTATION_HORIZONTAL == newDisplayParams.orientation && !newDisplayParams.useSsiv)
                isZoomFrameEnabled = true

            // Orientation changes
            if (isOrientationChange) {
                layoutMgr.orientation = getOrientation(newDisplayParams.orientation)

                // Resets the views to switch between paper roll mode (vertical) and independent page mode (horizontal)
                recyclerView.resetScale()

                if (VIEWER_ORIENTATION_VERTICAL == newDisplayParams.orientation) {
                    // For paper roll mode (vertical)
                    val onVerticalZoneTapListener =
                        OnZoneTapListener(recyclerView)
                            .setOnMiddleZoneTapListener { onMiddleTap() }
                            .setOnLongTapListener { onLongTap() }
                    recyclerView.setTapListener(onVerticalZoneTapListener)
                    adapter.setItemTouchListener(onVerticalZoneTapListener)
                    isZoomFrameEnabled = true
                    shouldUpdateImageDisplay = true
                } else {
                    // For independent images mode (horizontal)
                    val onHorizontalZoneTapListener =
                        OnZoneTapListener(
                            recyclerView,
                            if (Settings.isReaderTapToTurn2x) 2 else 1
                        )
                            .setOnLeftZoneTapListener { onLeftTap() }
                            .setOnRightZoneTapListener { onRightTap() }
                            .setOnMiddleZoneTapListener { onMiddleTap() }
                            .setOnLongTapListener { onLongTap() }
                    recyclerView.setTapListener(onHorizontalZoneTapListener)
                    adapter.setItemTouchListener(onHorizontalZoneTapListener)
                    isZoomFrameEnabled = isZoomFrameEnabled || newDisplayParams.twoPages
                    seekToIndex(absImageIndex)
                }
                pageSnapWidget.setPageSnapEnabled(VIEWER_ORIENTATION_HORIZONTAL == newDisplayParams.orientation)
            }

            zoomFrame.frameEnabled = isZoomFrameEnabled
            recyclerView.setLongTapZoomEnabled(!(Settings.isReaderHoldToZoom xor isZoomFrameEnabled))
            recyclerView.setDoubleTapZoomEnabled(!(Settings.isReaderDoubleTapToZoom xor isZoomFrameEnabled))

            // Direction changes
            if (isDirectionChange) {
                val currentLayoutDirection: Int =
                    if (View.LAYOUT_DIRECTION_LTR == controlsOverlay.root.layoutDirection) VIEWER_DIRECTION_LTR else VIEWER_DIRECTION_RTL
                layoutMgr.reverseLayout = newDisplayParams.direction != currentLayoutDirection

                adapter.setScrollLTR(VIEWER_DIRECTION_LTR == newDisplayParams.direction)
                navigator.setDirection(newDisplayParams.direction)
                navigator.updatePageControls()
            }
            if (isLayoutChange) {
                adapter.setTwoPagesMode(newDisplayParams.twoPages)
                shouldUpdateImageDisplay = true
                activity?.requestedOrientation =
                    if (newDisplayParams.twoPages) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                pageSnapWidget.setMaxFlingBlocks(if (newDisplayParams.twoPages) 2 else 1)
            }
            if (shouldUpdateImageDisplay) onUpdateImageDisplay(false)
        }
    }

    /**
     * Transforms current Preferences orientation into LinearLayoutManager orientation code
     *
     * @return Preferred orientation, as LinearLayoutManager orientation code
     */
    private fun getOrientation(orientation: Int): Int {
        return if (VIEWER_ORIENTATION_HORIZONTAL == orientation) {
            RecyclerView.HORIZONTAL
        } else {
            RecyclerView.VERTICAL
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
        powerMenu.setIconColor(
            ContextCompat.getColor(
                requireContext(),
                R.color.white_opacity_87
            )
        )
        binding?.apply {
            powerMenu.showAtCenter(recyclerView)
        }
    }

    /**
     * Load next page
     *
     * @return true if there's a page to load; false if there is none
     */
    override fun nextPage(): Boolean {
        val delta = if (displayParams?.twoPages == true) 2 else 1
        if (absImageIndex + delta > adapter.itemCount - 1) {
            if (Settings.isReaderContinuous) return navigator.nextContainer()
            return false
        }
        binding?.apply {
            if (Settings.isReaderTapTransitions) {
                if (VIEWER_ORIENTATION_HORIZONTAL == displayParams?.orientation)
                    recyclerView.smoothScrollToPosition(absImageIndex + delta)
                else {
                    smoothScroller.targetPosition = absImageIndex + delta
                    layoutMgr.startSmoothScroll(smoothScroller)
                }
            } else {
                if (VIEWER_ORIENTATION_HORIZONTAL == displayParams?.orientation)
                    recyclerView.scrollToPosition(absImageIndex + delta)
                else layoutMgr.scrollToPositionWithOffset(absImageIndex + delta, 0)
            }
        }
        return true
    }

    /**
     * Load previous page
     *
     * @return true if there's a page to load; false if there is none
     */
    override fun previousPage(): Boolean {
        val delta = if (displayParams?.twoPages == true) 2 else 1
        if (absImageIndex - delta < 0) {
            if (Settings.isReaderContinuous) return navigator.previousContainer()
            return false
        }
        binding?.apply {
            if (Settings.isReaderTapTransitions)
                recyclerView.smoothScrollToPosition(absImageIndex - delta)
            else recyclerView.scrollToPosition(absImageIndex - delta)
        }
        return true
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
                if (VIEWER_DIRECTION_LTR == Settings.getContentDirection(
                        bookSite,
                        bookPreferences
                    )
                ) {
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
                    previousImageView.load(previousImg.fileUri)
                    previousImageView.visibility = View.VISIBLE
                } else previousImageView.visibility = View.INVISIBLE
                if (currentImg != null) controlsOverlay.imagePreviewCenter.load(currentImg.fileUri)
                if (nextImg != null) {
                    nextImageView.load(nextImg.fileUri)
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

    /**
     * @return true if there's a book to load; false if there is none
     */
    override fun nextBook(): Boolean {
        return viewModel.loadNextContent(absImageIndex)
    }

    /**
     * @return true if there's a book to load; false if there is none
     */
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
     * Go back to book 1, page 1
     */
    override fun goToBookSelectionStart() {
        viewModel.loadFirstContent(absImageIndex)
    }

    /**
     * Handler for tapping on the left zone of the screen
     */
    private fun onLeftTap() {
        // Hide pending micro-menus
        hidePendingMicroMenus()

        // Stop slideshow if it is on
        if (slideshowMgr.isActive()) {
            slideshowMgr.stop()
            return
        }

        // Side-tapping disabled when view is zoomed
        binding?.apply {
            if (recyclerView.scale != 1f) return
        }
        // Side-tapping disabled when disabled in preferences
        if (!Settings.isReaderTapToTurn) return
        if (VIEWER_DIRECTION_LTR == Settings.getContentDirection(
                bookSite,
                bookPreferences
            )
        ) previousPage() else nextPage()
    }

    /**
     * Handler for tapping on the right zone of the screen
     */
    private fun onRightTap() {
        // Hide pending micro-menus
        hidePendingMicroMenus()

        // Stop slideshow if it is on
        if (slideshowMgr.isActive()) {
            slideshowMgr.stop()
            return
        }

        // Side-tapping disabled when view is zoomed
        binding?.apply {
            if (recyclerView.scale != 1f) return
        }
        // Side-tapping disabled when disabled in preferences
        if (!Settings.isReaderTapToTurn) return
        if (VIEWER_DIRECTION_LTR == Settings.getContentDirection(
                bookSite,
                bookPreferences
            )
        ) nextPage() else previousPage()
    }

    /**
     * Handler for tapping on the middle zone of the screen
     */
    private fun onMiddleTap() {
        binding?.apply {
            // Hide pending micro-menus
            hidePendingMicroMenus()

            // Stop slideshow if it is on
            if (slideshowMgr.isActive()) {
                slideshowMgr.stop()
                return
            }
            if (controlsOverlay.root.isVisible) hideControlsOverlay() else showControlsOverlay()
        }
    }

    /**
     * Handler for long-tapping the screen
     */
    private fun onLongTap() {
        if (!Settings.isReaderHoldToZoom) {
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

    override fun hideControlsOverlay() {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Settings.isReaderDisplayAroundNotch) {
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

    private fun onScaleChanged(scale: Float) {
        adapterRescaleDebouncer.submit(scale)
        if (LinearLayoutManager.HORIZONTAL == displayParams?.orientation) {
            pageSnapWidget.apply {
                if (scale - 1.0 < 0.05 && !isEnabled) setPageSnapEnabled(true)
                else if (scale - 1.0 > 0.05 && isEnabled) setPageSnapEnabled(false)
            }
        }
        if (VIEWER_ORIENTATION_VERTICAL == displayParams?.orientation) rescaleDebouncer.submit(scale)
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

    override fun setAndStartSmoothScroll(s: ReaderSmoothScroller) {
        smoothScroller = s
        layoutMgr.startSmoothScroll(s)
    }

    data class DisplayParams(
        val browseMode: Int,
        val displayMode: Int,
        private val twoPagesIn: Boolean,
        val isSmoothRendering: Boolean,
        val useSsiv: Boolean
    ) {
        val orientation =
            if (browseMode == Settings.Value.VIEWER_BROWSE_TTB) VIEWER_ORIENTATION_VERTICAL else VIEWER_ORIENTATION_HORIZONTAL
        val direction =
            if (browseMode == Settings.Value.VIEWER_BROWSE_RTL) VIEWER_DIRECTION_RTL else VIEWER_DIRECTION_LTR

        // Fix cases where book settings cumulated with default settings lead to incompatible values
        val twoPages = if (browseMode == Settings.Value.VIEWER_BROWSE_TTB) false else twoPagesIn
    }
}