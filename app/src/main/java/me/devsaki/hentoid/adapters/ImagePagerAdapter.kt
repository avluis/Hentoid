package me.devsaki.hentoid.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.SingletonImageLoader
import coil3.dispose
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowConversionToBitmap
import coil3.request.allowHardware
import coil3.request.target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.AutoRotateMethod
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.OnImageEventListener
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.ScaleType
import me.devsaki.hentoid.customssiv.uri
import me.devsaki.hentoid.customssiv.util.lifecycleScope
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.reader.ReaderPagerFragment
import me.devsaki.hentoid.gles_renderer.GPUImage
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DIRECTION_LTR
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DISPLAY_FILL
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DISPLAY_STRETCH
import me.devsaki.hentoid.util.Settings.Value.VIEWER_ORIENTATION_HORIZONTAL
import me.devsaki.hentoid.util.Settings.Value.VIEWER_ORIENTATION_VERTICAL
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SEPARATING_BARS_LARGE
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SEPARATING_BARS_MEDIUM
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SEPARATING_BARS_SMALL
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.getScreenDimensionsPx
import me.devsaki.hentoid.util.image.needsRotating
import me.devsaki.hentoid.util.pause
import me.devsaki.hentoid.views.ZoomableRecyclerView
import me.devsaki.hentoid.widget.OnZoneTapListener
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

private val IMAGE_DIFF_CALLBACK: DiffUtil.ItemCallback<ImageFile> =
    object : DiffUtil.ItemCallback<ImageFile>() {
        override fun areItemsTheSame(
            oldItem: ImageFile, newItem: ImageFile
        ): Boolean {
            return oldItem.uniqueHash() == newItem.uniqueHash()
        }

        override fun areContentsTheSame(
            oldItem: ImageFile, newItem: ImageFile
        ): Boolean {
            return (oldItem == newItem)
        }
    }

class ImagePagerAdapter(context: Context) :
    ListAdapter<ImageFile, ImagePagerAdapter.ImageViewHolder>(IMAGE_DIFF_CALLBACK) {

    enum class ImageType(val value: Int) {
        IMG_TYPE_OTHER(0), // PNGs, JPEGs and WEBPs -> use CustomSubsamplingScaleImageView; will fallback to Coil if animation detected
        IMG_TYPE_GIF(1), // Static and animated GIFs -> use Coil
        IMG_TYPE_APNG(2), // Animated PNGs -> use Coil
        IMG_TYPE_AWEBP(3), // Animated WEBPs -> use Coil
        IMG_TYPE_JXL(4) // JXL -> use Coil
    }

    private val pageMinHeight = context.resources.getDimension(R.dimen.page_min_height).toInt()
    private val screenWidth: Int
    private val screenHeight: Int

    private var itemTouchListener: OnZoneTapListener? = null
    private var scaleListener: BiConsumer<Int, Float>? = null

    private var recyclerView: ZoomableRecyclerView? = null
    private val initialAbsoluteScales: MutableMap<Int, Float> = HashMap()
    private val absoluteScales: MutableMap<Int, Float> = HashMap()
    private var isGlInit = false
    private val glEsRenderer: GPUImage by lazy {
        isGlInit = true
        GPUImage(context)
    }

    // To preload images before they appear on screen with CustomSubsamplingScaleImageView
    private var maxBitmapWidth = -1
    private var maxBitmapHeight = -1

    // Direction user is curently reading the book with
    private var isScrollLTR = true

    // Cached prefs
    private var colorDepth = Bitmap.Config.RGB_565
    private var separatingBarsHeight = 0
    private var twoPagesMode = false

    private var doubleTapZoomEnabled = false
    private var longTapZoomEnabled = false
    private var autoRotate = Settings.Value.READER_AUTO_ROTATE_NONE
    private var doubleTapZoomCap = 0f


    init {
        getScreenDimensionsPx(context).let {
            screenWidth = it.x
            screenHeight = it.y
        }
        refreshPrefs()
    }

    // Book prefs have to be set explicitely because the cached Content linked from each ImageFile
    // might not have the latest properties
    fun refreshPrefs() {
        val separatingBarsPrefs = Settings.readerSeparatingBars
        separatingBarsHeight = when (separatingBarsPrefs) {
            VIEWER_SEPARATING_BARS_SMALL -> 4
            VIEWER_SEPARATING_BARS_MEDIUM -> 16
            VIEWER_SEPARATING_BARS_LARGE -> 64
            else -> 0
        }
        doubleTapZoomEnabled = Settings.isReaderDoubleTapToZoom
        longTapZoomEnabled = Settings.isReaderHoldToZoom
        autoRotate = Settings.readerAutoRotate

        val doubleTapZoomCapCode = Settings.readerCapTapZoom
        doubleTapZoomCap =
            if (Settings.Value.VIEWER_CAP_TAP_ZOOM_NONE == doubleTapZoomCapCode) -1f else doubleTapZoomCapCode.toFloat()

        colorDepth =
            if (0 == Settings.readerColorDepth) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    }

    fun setRecyclerView(v: ZoomableRecyclerView?) {
        recyclerView = v
    }

    fun setItemTouchListener(itemTouchListener: OnZoneTapListener?) {
        this.itemTouchListener = itemTouchListener
    }

    private fun getImageType(img: ImageFile?): ImageType {
        if (null == img) return ImageType.IMG_TYPE_OTHER
        val extension = getExtension(img.fileUri)
        if ("gif".equals(extension, ignoreCase = true) || img.mimeType.contains("gif")) {
            return ImageType.IMG_TYPE_GIF
        }
        if ("apng".equals(extension, ignoreCase = true) || img.mimeType.contains("apng")) {
            return ImageType.IMG_TYPE_APNG
        }
        return if ("jxl".equals(extension, ignoreCase = true) || img.mimeType.contains("jxl")) {
            return ImageType.IMG_TYPE_JXL
        } else ImageType.IMG_TYPE_OTHER
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        val view = inflater.inflate(R.layout.item_reader_image, viewGroup, false)
        view.setViewTreeLifecycleOwner(viewGroup.findViewTreeLifecycleOwner())
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        Timber.d("Picture $position : BindViewHolder")

        val displayParams = getDisplayParamsForPosition(position) ?: return

        holder.reset()
        holder.bind(displayParams, position)
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        // Set the holder back to its original constraints while in vertical mode
        // (not doing this will cause super high memory usage by trying to load _all_ images)
        if (VIEWER_ORIENTATION_VERTICAL == holder.viewerOrientation) {
            holder.rootView.minimumHeight = pageMinHeight
            val layoutParams = holder.rootView.layoutParams
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            holder.rootView.layoutParams = layoutParams
        }

        // Free the SSIV's resources
        holder.clear()
        super.onViewRecycled(holder)
    }

    fun getImageAt(index: Int): ImageFile? {
        return if (index in 0 until itemCount) getItem(index) else null
    }

    fun destroy() {
        if (isGlInit) glEsRenderer.clear()
        scaleListener = null
        itemTouchListener = null
    }

    fun reset() {
        absoluteScales.clear()
        initialAbsoluteScales.clear()
    }

    private fun getDisplayParamsForPosition(position: Int): ReaderPagerFragment.DisplayParams? {
        val img = getItem(position)
        val content = img.linkedContent
        if (content != null) {
            val bookPreferences = content.bookPreferences
            return ReaderPagerFragment.DisplayParams(
                Settings.getContentBrowseMode(bookPreferences),
                Settings.getContentDisplayMode(bookPreferences),
                Settings.getContent2PagesMode(bookPreferences),
                Settings.isContentSmoothRendering(bookPreferences),
                false
            )
        }
        return null
    }

    fun getDimensionsAtPosition(position: Int): Point {
        (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.let {
            return it.dimensions
        }
        return Point()
    }

    fun getAbsoluteScaleAtPosition(position: Int): Float {
        if (absoluteScales.containsKey(position)) {
            val result = absoluteScales[position]
            if (result != null) return result
        } else (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.let {
            return it.absoluteScale
        }
        return 0f
    }

    fun getRelativeScaleAtPosition(position: Int): Float {
        if (absoluteScales.containsKey(position)) {
            val resultInitial = initialAbsoluteScales[position]
            val result = absoluteScales[position]
            if (result != null && resultInitial != null) return result / resultInitial
        }
        return 0f
    }

    fun setRelativeScaleAtPosition(position: Int, targetRelativeScale: Float) {
        recyclerView?.apply {
            (findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.let { holder ->
                if (initialAbsoluteScales.containsKey(position)) {
                    initialAbsoluteScales[position]?.let { initialScale ->
                        holder.absoluteScale = targetRelativeScale * initialScale
                    }
                }
            }
        }
    }

    suspend fun getSSivAtPosition(position: Int): Boolean = withContext(Dispatchers.Default) {
        (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.isImageView() == false
    }

    fun resetScaleAtPosition(position: Int) {
        (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.resetScale()
    }

    fun multiplyScale(multiplier: Float) {
        for (i in 0 until itemCount) {
            (recyclerView?.findViewHolderForAdapterPosition(i) as ImageViewHolder?)
                ?.multiplyVirtualScale(multiplier)
        }
    }

    private fun onAbsoluteScaleChanged(position: Int, scale: Float) {
        Timber.d(">> position %d -> scale %s", position, scale)
        if (!initialAbsoluteScales.containsKey(position)) initialAbsoluteScales[position] = scale
        if (!absoluteScales.containsKey(position)) absoluteScales[position] = scale
        if (abs(scale - absoluteScales[position]!!) > 0.01) {
            absoluteScales[position] = scale
            scaleListener?.invoke(position, scale)
        }
    }

    fun setOnScaleListener(scaleListener: BiConsumer<Int, Float>?) {
        this.scaleListener = scaleListener
    }

    fun setMaxDimensions(maxWidth: Int, maxHeight: Int) {
        maxBitmapWidth = maxWidth
        maxBitmapHeight = maxHeight
    }

    fun setScrollLTR(isScrollLTR: Boolean) {
        this.isScrollLTR = isScrollLTR
    }

    fun setTapBehaviourForPosition(position: Int, immediate: Boolean = false) {
        recyclerView?.lifecycleScope?.launch {
            if (!immediate) {
                withContext(Dispatchers.Default) {
                    // Account for items being refreshed just after that call
                    // NB : A cleaner implementation would be to intercept all notifyxxxChanged calls and set a debouncer on them,
                    // but it would add complexity for little perceived value
                    pause(500)
                }
            }
            (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.apply {
                Timber.d("adjustBehaviourForPosition $position")
                setTapListener()
            }
        }
    }

    fun setTwoPagesMode(value: Boolean) {
        if (twoPagesMode != value) twoPagesMode = value
    }

    // ====================== VIEWHOLDER

    inner class ImageViewHolder(val rootView: View) : RecyclerView.ViewHolder(rootView),
        OnImageEventListener {
        private val ssiv: CustomSubsamplingScaleImageView = rootView.requireById(R.id.ssiv)
        private val imageView: ImageView = rootView.requireById(R.id.imageview)
        private val noImgTxt: TextView = rootView.requireById(R.id.viewer_no_page_txt)

        private lateinit var imgView: View

        private var displayMode = 0
        var viewerOrientation = 0
        private var isSmoothRendering = false
        private var isHalfWidth = false

        private var isImageView = false
        private var forceImageView: Boolean? = null
        private var img: ImageFile? = null
        private var scaleMultiplier = 1f // When used with ZoomableFrame in vertical mode

        private var isLoading = AtomicBoolean(false)

        fun bind(
            displayParams: ReaderPagerFragment.DisplayParams,
            position: Int
        ) {
            viewerOrientation = displayParams.orientation
            displayMode = displayParams.displayMode
            isSmoothRendering = displayParams.isSmoothRendering
            isHalfWidth = displayParams.twoPages
            val isVertical = VIEWER_ORIENTATION_VERTICAL == viewerOrientation

            if (isVertical) {
                ssiv.setDirection(CustomSubsamplingScaleImageView.Direction.VERTICAL)
                // Avoid stacking 0-px tall images on screen and load all of them at the same time
                rootView.minimumHeight = pageMinHeight
            }
            // ImageView shouldn't react to click events when in vertical mode (controlled by ZoomableFrame / ZoomableRecyclerView)
            imageView.isClickable = !(isVertical || isHalfWidth)
            imageView.isFocusable = !(isVertical || isHalfWidth)

            // WARNING following line must be coherent with what happens in setTapListener
            ssiv.setIgnoreTouchEvents(isVertical || isHalfWidth)

            val imageType = getImageType(getImageAt(position))
            if (forceImageView != null) { // ImageView has been forced
                switchImageView(forceImageView!!, true)
                forceImageView = null // Reset force flag
            } else if (ImageType.IMG_TYPE_GIF == imageType || ImageType.IMG_TYPE_APNG == imageType || ImageType.IMG_TYPE_AWEBP == imageType || ImageType.IMG_TYPE_JXL == imageType) {
                switchImageView(isImageView = true, isClickThrough = true)
            } else switchImageView(false, false)

            // Initialize SSIV when required
            if (!isVertical && !isImageView) {
                ssiv.setGlEsRenderer(if (isSmoothRendering) glEsRenderer else null)
                // Only valid for horizontal
                ssiv.setPreloadDimensions(
                    if (isHalfWidth) screenWidth / 2 else screenWidth,
                    screenHeight
                )
                if (!Settings.isReaderZoomTransitions) ssiv.setDoubleTapZoomDuration(10)

                val scrollLTR = VIEWER_DIRECTION_LTR == displayParams.direction && isScrollLTR
                ssiv.setOffsetLeftSide(scrollLTR)
                ssiv.setScaleListener { s -> onAbsoluteScaleChanged(position, s) }
            }

            // Image layout constraints
            // NB : Will be rewritten once images have been loaded
            val layoutStyle =
                if (isVertical) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            val layoutParams = imgView.layoutParams
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = layoutStyle
            imgView.layoutParams = layoutParams

            var imageAvailable = true
            val img = getImageAt(position)
            if (img != null && img.fileUri.isNotEmpty())
                setImage(img, ImageType.IMG_TYPE_JXL == imageType)
            else imageAvailable = false

            val isStreaming = img != null && !imageAvailable && img.status == StatusContent.ONLINE
            val isExtracting = img != null && !imageAvailable && !img.url.startsWith("http")

            @StringRes var text: Int = R.string.image_not_found
            if (isStreaming) text = R.string.image_streaming
            else if (isExtracting) text = R.string.image_extracting
            noImgTxt.setText(text)
            noImgTxt.isVisible = !imageAvailable
        }

        private fun setImage(img: ImageFile, isJxl: Boolean) {
            this.img = img
            val imgType = getImageType(img)
            val uri = Uri.parse(img.fileUri)
            isLoading.set(true)
            noImgTxt.isVisible = false
            Timber.d("Picture $absoluteAdapterPosition : binding viewholder $imgType $uri")
            if (!isImageView) { // SubsamplingScaleImageView
                Timber.d("Picture $absoluteAdapterPosition : Using SSIV")
                ssiv.recycle()
                ssiv.setMinimumScaleType(ssivScaleType)
                ssiv.setOnImageEventListener(this@ImageViewHolder)
                ssiv.setDoubleTapZoomEnabled(doubleTapZoomEnabled)
                ssiv.setLongTapZoomEnabled(longTapZoomEnabled)
                ssiv.setDoubleTapZoomCap(doubleTapZoomCap)
                ssiv.setAutoRotate(
                    when (autoRotate) {
                        Settings.Value.READER_AUTO_ROTATE_LEFT -> AutoRotateMethod.LEFT
                        Settings.Value.READER_AUTO_ROTATE_RIGHT -> AutoRotateMethod.RIGHT
                        else -> AutoRotateMethod.NONE
                    }
                )
                // 120 dpi = equivalent to the web browser's max zoom level
                ssiv.setMinimumDpi(120)
                ssiv.setDoubleTapZoomDpi(120)
                if (maxBitmapWidth > 0) ssiv.setMaxTileSize(maxBitmapWidth, maxBitmapHeight)
                ssiv.setImage(uri(uri))
            } else { // ImageView
                val view = imgView as ImageView
                Timber.d("Picture $absoluteAdapterPosition : Using Coil")

                val isChangeDims = when (autoRotate) {
                    Settings.Value.READER_AUTO_ROTATE_NONE -> false
                    else -> {
                        // Preload the pic to get its dimensions
                        val dims = view.context.let { ctx ->
                            val request = ImageRequest.Builder(ctx)
                                .data(uri)
                                .diskCacheKey(uri.toString())
                                .memoryCacheKey(uri.toString())
                                .allowHardware(!isJxl)
                                .allowConversionToBitmap(false)
                                .build()
                            // TODO does that block the UI thread?
                            view.context.imageLoader.executeBlocking(request).image?.let {
                                Point(it.width, it.height)
                            } ?: Point(0, 0)
                        }
                        needsRotating(screenWidth, screenHeight, dims.x, dims.y)
                    }
                }

                recyclerView?.let {
                    val imgLayoutParams = imageView.layoutParams
                    imgLayoutParams.width =
                        if (isChangeDims) it.height else ViewGroup.LayoutParams.MATCH_PARENT
                    imgLayoutParams.height =
                        if (isChangeDims) it.width else ViewGroup.LayoutParams.MATCH_PARENT
                    imageView.layoutParams = imgLayoutParams
                }

                imageView.scaleType = scaleType

                // Custom loader to handle JXL
                // (doesn't support Hardware bitmaps : https://github.com/awxkee/jxl-coder-coil/issues/7)
                view.context.let { ctx ->
                    val imageLoader = SingletonImageLoader.get(ctx)
                    val request = ImageRequest.Builder(ctx)
                        .data(uri)
                        .diskCacheKey(uri.toString())
                        .memoryCacheKey(uri.toString())
                        .target(imageView)
                        .allowHardware(!isJxl)
                        .listener(
                            onError = { _, err -> onCoilLoadFailed(err) },
                            onSuccess = { _, res -> onCoilLoadSuccess(res) }
                        )
                        .allowConversionToBitmap(false)
                    imageLoader.enqueue(request.build())
                }
            }
        }

        suspend fun setTapListener() {
            // ImageView or vertical mode => ZoomableRecycleView handles gestures
            if (isImageView() || VIEWER_ORIENTATION_VERTICAL == viewerOrientation || isHalfWidth) {
                Timber.d("$absoluteAdapterPosition setTapListener on recyclerView")
                recyclerView?.setTapListener(itemTouchListener)
                setTapListener(true)
            } else { // Single-page, horizontal SSIV => SSIV handles gestures
                Timber.d("$absoluteAdapterPosition setTapListener on imageView")
                recyclerView?.setTapListener(null)
                setTapListener(false)
            }
        }

        fun setTapListener(isImageView: Boolean) {
            recyclerView?.lifecycleScope?.launch {
                withContext(Dispatchers.Default) {
                    var iterations = 0 // Wait for 5 secs max
                    while (isLoading.get() && iterations++ < 33) pause(150)
                }
                withContext(Dispatchers.Main) {
                    imgView.setOnTouchListener(if (isImageView) null else itemTouchListener)
                }
            }
        }

        suspend fun isImageView(): Boolean = withContext(Dispatchers.Default) {
            var iterations = 0 // Wait for 5 secs max
            while (isLoading.get() && iterations++ < 33) pause(150)
            isImageView
        }

        private val ssivScaleType: ScaleType
            get() = if (VIEWER_DISPLAY_FILL == displayMode) ScaleType.SMART_FILL
            else if (VIEWER_DISPLAY_STRETCH == displayMode) ScaleType.STRETCH_SCREEN
            else ScaleType.CENTER_INSIDE

        private val scaleType: ImageView.ScaleType
            get() = if (VIEWER_DISPLAY_FILL == displayMode) ImageView.ScaleType.FIT_CENTER
            else if (VIEWER_DISPLAY_STRETCH == displayMode) ImageView.ScaleType.FIT_XY
            else ImageView.ScaleType.FIT_CENTER

        // ImageView
        // TODO doesn't work for Coil as it doesn't use ImageView's scaling
        var absoluteScale: Float
            get() {
                return if (!isImageView) {
                    ssiv.getVirtualScale()
                } else { // ImageView
                    imageView.scaleX
                }
            }
            set(targetScale) {
                if (!isImageView) {
                    ssiv.setScaleAndCenter(targetScale, null)
                } else { // ImageView
                    imageView.scaleX = targetScale
                }
            }

        fun resetScale() {
            if (!isImageView) {
                if (ssiv.isImageLoaded() && ssiv.isReady() && ssiv.isLaidOut) {
                    scaleMultiplier = 0f
                    ssiv.resetScale()
                }
            }
        }

        fun multiplyVirtualScale(multiplier: Float) {
            if (!isImageView) {
                if (ssiv.isImageLoaded() && ssiv.isReady() && ssiv.isLaidOut) {
                    val rawScale = ssiv.getVirtualScale() / scaleMultiplier
                    ssiv.setVirtualScale(rawScale * multiplier)
                    scaleMultiplier = multiplier
                }
            }
        }

        // ImageView
        val dimensions: Point
            get() {
                return if (!isImageView) {
                    Point(ssiv.width, ssiv.height)
                } else { // ImageView
                    Point(imageView.width, imageView.height)
                }
            }

        private fun getTargetScale(imgWidth: Int, imgHeight: Int, displayMode: Int): Float {
            return if (VIEWER_DISPLAY_FILL == displayMode) { // Fill screen
                if (imgHeight > imgWidth) {
                    // Fit to width
                    screenWidth / imgWidth.toFloat()
                } else {
                    if (screenHeight > screenWidth) screenHeight / imgHeight.toFloat() // Fit to height when in portrait mode
                    else screenWidth / imgWidth.toFloat() // Fit to width when in landscape mode
                }
            } else { // Fit screen
                (screenWidth / imgWidth.toFloat()).coerceAtMost(screenHeight / imgHeight.toFloat())
            }
        }

        private fun switchImageView(isImageView: Boolean, isClickThrough: Boolean = false) {
            Timber.d(
                "Picture %d : switching to %s (%s)",
                absoluteAdapterPosition,
                if (isImageView) "imageView" else "ssiv",
                isClickThrough
            )
            ssiv.isVisible = !isImageView
            imageView.isVisible = isImageView
            imgView = if (isImageView) imageView else ssiv
            if (isImageView) {
                // ImageView shouldn't react to click events when frame is zoomable (controlled by ZoomableFrame / ZoomableRecyclerView)
                imageView.isClickable = !isClickThrough
                imageView.isFocusable = !isClickThrough
            }
            setTapListener(isImageView)
            this.isImageView = isImageView
        }

        private fun forceImageView() {
            switchImageView(true)
            forceImageView = true
        }

        fun clear() {
            if (isImageView) imageView.dispose()
            else ssiv.clear()
            isLoading.set(false)
        }

        @SuppressLint("ClickableViewAccessibility")
        fun reset() {
            clear()
            imageView.isClickable = true
            imageView.isFocusable = true
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.setOnTouchListener(null)
            imageView.rotation = 0f

            ssiv.setIgnoreTouchEvents(false)
            ssiv.setDirection(CustomSubsamplingScaleImageView.Direction.HORIZONTAL)
            ssiv.setPreferredBitmapConfig(colorDepth)
            ssiv.setDoubleTapZoomDuration(500)
            ssiv.setOnTouchListener(null)

            rootView.minimumHeight = 0
            noImgTxt.isVisible = false
        }

        private fun adjustDimensions(
            imgWidth: Int,
            imgHeight: Int,
            adjustImgHeight: Boolean,
            resizeSmallPics: Boolean,
            doAutoRotate: Boolean
        ) {
            imgView.rotation =
                if (doAutoRotate && needsRotating(screenWidth, screenHeight, imgWidth, imgHeight)) {
                    when (autoRotate) {
                        Settings.Value.READER_AUTO_ROTATE_LEFT -> 90f
                        Settings.Value.READER_AUTO_ROTATE_RIGHT -> -90f
                        else -> 0f
                    }
                } else {
                    0f
                }

            // Root view layout
            val rootLayoutStyle =
                if (VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            val layoutParams = rootView.layoutParams
            layoutParams.width =
                if (isHalfWidth) screenWidth / 2 else ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = rootLayoutStyle
            rootView.layoutParams = layoutParams

            // Image view height (for vertical mode)
            if (adjustImgHeight) {
                var targetImgHeight = imgHeight
                // If we display a picture smaller than the screen dimensions, we have to zoom it
                if (resizeSmallPics && imgHeight < screenHeight && imgWidth < screenWidth) {
                    targetImgHeight =
                        (imgHeight * getTargetScale(imgWidth, imgHeight, displayMode)).roundToInt()
                    val imgLayoutParams = imgView.layoutParams
                    imgLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    imgLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    imgView.layoutParams = imgLayoutParams
                }
                rootView.minimumHeight = targetImgHeight + separatingBarsHeight
            }
        }

        // == SUBSAMPLINGSCALEVIEW CALLBACKS
        override fun onReady() {
            isLoading.set(false) // All that's left is to load tiles => consider the job done already
            val scaleView = imgView as CustomSubsamplingScaleImageView
            adjustDimensions(
                0,
                (scaleView.getAbsoluteScale() * scaleView.getSHeight()).toInt(),
                VIEWER_ORIENTATION_VERTICAL == viewerOrientation,
                false,
                false
            )
        }

        override fun onImageLoaded() {
            isLoading.set(false)
        }

        override fun onPreviewLoadError(e: Throwable) {
            // Nothing special
        }

        override fun onImageLoadError(e: Throwable) {
            Timber.d(
                e,
                "Picture %d : SSIV loading failed; reloading on ImageView : %s",
                absoluteAdapterPosition,
                img!!.fileUri
            )
            // Fall back to ImageView
            forceImageView()
            // Reload adapter
            notifyItemChanged(layoutPosition)
        }

        override fun onTileLoadError(e: Throwable) {
            Timber.d(e, "Picture %d : tileLoad error", absoluteAdapterPosition)
        }

        override fun onPreviewReleased() {
            // Nothing special
        }

        // == COIL CALLBACKS
        private fun onCoilLoadFailed(err: ErrorResult) {
            Timber.d(
                err.throwable,
                "Picture %d : Coil loading failed : %s",
                absoluteAdapterPosition,
                img!!.fileUri
            )
            if (isImageView) noImgTxt.visibility = View.VISIBLE
            isLoading.set(false)
        }

        private fun onCoilLoadSuccess(result: SuccessResult): Boolean {
            noImgTxt.visibility = View.GONE
            adjustDimensions(
                result.image.width,
                result.image.height,
                VIEWER_ORIENTATION_VERTICAL == viewerOrientation,
                true,
                VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation
            )
            isLoading.set(false)
            return false
        }
    }
}