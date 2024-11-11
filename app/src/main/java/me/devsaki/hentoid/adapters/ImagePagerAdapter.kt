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
import coil3.dispose
import coil3.load
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.request.allowConversionToBitmap
import coil3.request.transformations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.OnImageEventListener
import me.devsaki.hentoid.customssiv.uri
import me.devsaki.hentoid.customssiv.util.lifecycleScope
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.reader.ReaderPagerFragment
import me.devsaki.hentoid.gles_renderer.GPUImage
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.getScreenDimensionsPx
import me.devsaki.hentoid.util.image.SmartRotateTransformation
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
        IMG_TYPE_GIF(1), // Static and animated GIFs -> use APNG4Android library
        IMG_TYPE_APNG(2), // Animated PNGs -> use APNG4Android library
        IMG_TYPE_AWEBP(3) // Animated WEBPs -> use APNG4Android library
    }

    enum class ViewType(val value: Int) {
        DEFAULT(0), IMAGEVIEW_STRETCH(1), SSIV_VERTICAL(2)
    }

    private val pageMinHeight = context.resources.getDimension(R.dimen.page_min_height).toInt()
    private val screenWidth = getScreenDimensionsPx(context).x
    private val screenHeight = getScreenDimensionsPx(context).y

    private var itemTouchListener: OnZoneTapListener? = null
    private var scaleListener: BiConsumer<Int, Float>? = null
    private var ssivAlertListener: Runnable? = null

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

    private var longTapZoomEnabled = false
    private var autoRotate = false
    private var doubleTapZoomCap = 0f


    init {

        refreshPrefs()
    }

    // Book prefs have to be set explicitely because the cached Content linked from each ImageFile
    // might not have the latest properties
    fun refreshPrefs() {
        val separatingBarsPrefs = Preferences.getReaderSeparatingBars()
        separatingBarsHeight = when (separatingBarsPrefs) {
            Preferences.Constant.VIEWER_SEPARATING_BARS_SMALL -> 4
            Preferences.Constant.VIEWER_SEPARATING_BARS_MEDIUM -> 16
            Preferences.Constant.VIEWER_SEPARATING_BARS_LARGE -> 64
            else -> 0
        }
        longTapZoomEnabled = Preferences.isReaderHoldToZoom()
        autoRotate = Preferences.isReaderAutoRotate()

        val doubleTapZoomCapCode = Preferences.getReaderCapTapZoom()
        doubleTapZoomCap =
            if (Preferences.Constant.VIEWER_CAP_TAP_ZOOM_NONE == doubleTapZoomCapCode) -1f else doubleTapZoomCapCode.toFloat()

        colorDepth =
            if (0 == Settings.readerColorDepth) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    }

    fun setRecyclerView(v: ZoomableRecyclerView?) {
        recyclerView = v
    }

    fun setItemTouchListener(itemTouchListener: OnZoneTapListener?) {
        this.itemTouchListener = itemTouchListener
    }

    fun setSsivAlertListener(ssivAlertListener: Runnable) {
        this.ssivAlertListener = ssivAlertListener
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
        return if ("webp".equals(extension, ignoreCase = true) || img.mimeType.contains("webp")) {
            ImageType.IMG_TYPE_AWEBP
        } else ImageType.IMG_TYPE_OTHER
    }

    private fun getImageViewType(displayParams: ReaderPagerFragment.DisplayParams): ViewType {
        return if (Preferences.Constant.VIEWER_DISPLAY_STRETCH == displayParams.displayMode) ViewType.IMAGEVIEW_STRETCH
        else if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == displayParams.orientation) ViewType.SSIV_VERTICAL
        else ViewType.DEFAULT
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
        val imgViewType = getImageViewType(displayParams)

        holder.reset()
        holder.bind(displayParams, imgViewType, position)
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        // Set the holder back to its original constraints while in vertical mode
        // (not doing this will cause super high memory usage by trying to load _all_ images)
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == holder.viewerOrientation) {
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
        ssivAlertListener = null
        itemTouchListener = null
    }

    fun reset() {
        absoluteScales.clear()
        initialAbsoluteScales.clear()
    }

    private fun getDisplayParamsForPosition(position: Int): ReaderPagerFragment.DisplayParams? {
        val img = getItem(position)
        val content = img.content.reach(img)
        if (content != null) {
            val bookPreferences = content.bookPreferences
            return ReaderPagerFragment.DisplayParams(
                Preferences.getContentBrowseMode(bookPreferences),
                Preferences.getContentDisplayMode(bookPreferences),
                Settings.getContent2PagesMode(bookPreferences),
                Preferences.isContentSmoothRendering(bookPreferences),
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

    fun getSSivAtPosition(position: Int): Boolean {
        var res = false
        recyclerView?.lifecycleScope?.launch {
            res =
                (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.isImageView()
                    ?: false
        }
        return res
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

    fun setGestureListenerForPosition(position: Int) {
        recyclerView?.lifecycleScope?.launch {
            (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.setTapListener()
        }
    }

    fun setTwoPagesMode(value: Boolean) {
        if (twoPagesMode != value) twoPagesMode = value
    }

    // ====================== VIEWHOLDER

    inner class ImageViewHolder(val rootView: View) : RecyclerView.ViewHolder(rootView),
        OnImageEventListener {
        private val ssiv: CustomSubsamplingScaleImageView = itemView.requireById(R.id.ssiv)
        private val imageView: ImageView = itemView.requireById(R.id.imageview)
        private val noImgTxt: TextView = itemView.requireById(R.id.viewer_no_page_txt)

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
            imgViewType: ViewType,
            position: Int
        ) {
            viewerOrientation = displayParams.orientation
            displayMode = displayParams.displayMode
            isSmoothRendering = displayParams.isSmoothRendering
            isHalfWidth = displayParams.twoPages

            if (ViewType.DEFAULT == imgViewType) {
                // ImageView shouldn't react to click events when in vertical mode (controlled by ZoomableFrame / ZoomableRecyclerView)
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                    imageView.isClickable = false
                    imageView.isFocusable = false
                }
            } else if (ViewType.IMAGEVIEW_STRETCH == imgViewType) {
                imageView.scaleType = ImageView.ScaleType.FIT_XY
            } else if (ViewType.SSIV_VERTICAL == imgViewType) {
                ssiv.setIgnoreTouchEvents(true)
                ssiv.setDirection(CustomSubsamplingScaleImageView.Direction.VERTICAL)
            }

            // Avoid stacking 0-px tall images on screen and load all of them at the same time
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation)
                rootView.minimumHeight = pageMinHeight

            val imageType = getImageType(getImageAt(position))
            if (forceImageView != null) { // ImageView has been forced
                switchImageView(forceImageView!!, true)
                forceImageView = null // Reset force flag
            } else if (ImageType.IMG_TYPE_GIF == imageType || ImageType.IMG_TYPE_APNG == imageType) {
                ssivAlertListener?.run()
                switchImageView(isImageView = true, isClickThrough = true)
            } else switchImageView(
                imgViewType == ViewType.IMAGEVIEW_STRETCH,
                imgViewType == ViewType.IMAGEVIEW_STRETCH
            )

            // Initialize SSIV when required
            if (imgViewType == ViewType.DEFAULT && Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation && !isImageView) {
                if (isSmoothRendering) ssiv.setGlEsRenderer(glEsRenderer)
                else ssiv.setGlEsRenderer(null)
                ssiv.setPreloadDimensions(itemView.width, imgView.height)
                if (!Preferences.isReaderZoomTransitions()) ssiv.setDoubleTapZoomDuration(10)

                val scrollLTR =
                    Preferences.Constant.VIEWER_DIRECTION_LTR == displayParams.direction && isScrollLTR
                ssiv.setOffsetLeftSide(scrollLTR)
                ssiv.setScaleListener { s -> onAbsoluteScaleChanged(position, s.toFloat()) }
            }

            // Image layout constraints
            // NB : Will be rewritten once images have been loaded
            val layoutStyle =
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            val layoutParams = imgView.layoutParams
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = layoutStyle
            imgView.layoutParams = layoutParams

            var imageAvailable = true
            val img = getImageAt(position)
            if (img != null && img.fileUri.isNotEmpty()) setImage(img)
            else imageAvailable = false

            val isStreaming = img != null && !imageAvailable && img.status == StatusContent.ONLINE
            val isExtracting = img != null && !imageAvailable && !img.url.startsWith("http")

            @StringRes var text: Int = R.string.image_not_found
            if (isStreaming) text = R.string.image_streaming
            else if (isExtracting) text = R.string.image_extracting
            noImgTxt.setText(text)
            noImgTxt.isVisible = !imageAvailable
        }

        private fun setImage(img: ImageFile) {
            this.img = img
            val imgType = getImageType(img)
            val uri = Uri.parse(img.fileUri)
            isLoading.set(true)
            noImgTxt.isVisible = false
            Timber.d("Picture $absoluteAdapterPosition : binding viewholder $imgType $uri")
            if (!isImageView) { // SubsamplingScaleImageView
                Timber.d("Picture $absoluteAdapterPosition : Using SSIV")
                ssiv.recycle()
                ssiv.setMinimumScaleType(scaleType)
                ssiv.setOnImageEventListener(this)
                ssiv.setLongTapZoomEnabled(longTapZoomEnabled)
                ssiv.setDoubleTapZoomCap(doubleTapZoomCap)
                ssiv.setAutoRotate(autoRotate)
                // 120 dpi = equivalent to the web browser's max zoom level
                ssiv.setMinimumDpi(120)
                ssiv.setDoubleTapZoomDpi(120)
                if (maxBitmapWidth > 0) ssiv.setMaxTileSize(maxBitmapWidth, maxBitmapHeight)
                ssiv.setImage(uri(uri))
            } else { // ImageView
                val view = imgView as ImageView
                Timber.d("Picture $absoluteAdapterPosition : Using Coil")
                val transformation = if (autoRotate) listOf(
                    SmartRotateTransformation(
                        90f,
                        screenWidth,
                        screenHeight
                    )
                ) else emptyList()
                view.load(uri) {
                    transformations(transformation)
                    listener(
                        onError = { _, err -> onCoilLoadFailed(err) },
                        onSuccess = { _, res -> onCoilLoadSuccess(res) }
                    )
                    allowConversionToBitmap(false)
                }
            }
        }

        suspend fun setTapListener() {
            // ImageView or vertical mode => ZoomableRecycleView handles gestures
            if (isImageView() || Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                Timber.d("$absoluteAdapterPosition setTapListener on recyclerView")
                recyclerView?.setTapListener(itemTouchListener)
                imgView.setOnTouchListener(null)
            } else { // Horizontal SSIV => SSIV handles gestures
                Timber.d("$absoluteAdapterPosition setTapListener on imageView")
                recyclerView?.setTapListener(null)
                imgView.setOnTouchListener(itemTouchListener)
            }
        }

        suspend fun isImageView(): Boolean {
            withContext(Dispatchers.Default) {
                var iterations = 0 // Wait for 5 secs max
                while (isLoading.get() && iterations++ < 33) pause(150)
            }
            return isImageView
        }

        private val scaleType: CustomSubsamplingScaleImageView.ScaleType
            get() = if (Preferences.Constant.VIEWER_DISPLAY_FILL == displayMode) {
                CustomSubsamplingScaleImageView.ScaleType.SMART_FILL
            } else {
                CustomSubsamplingScaleImageView.ScaleType.CENTER_INSIDE
            }

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

        private fun adjustDimensions(
            imgWidth: Int,
            imgHeight: Int,
            adjustImgHeight: Boolean,
            resizeSmallPics: Boolean
        ) {
            // Root view layout
            val rootLayoutStyle =
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            val effectiveWidth = screenWidth
//                if (Configuration.ORIENTATION_LANDSCAPE == rootView.context.resources.configuration.orientation) screenHeight else screenWidth
            val layoutParams = rootView.layoutParams
            layoutParams.width =
                if (isHalfWidth) effectiveWidth / 2 else ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = rootLayoutStyle
            Timber.i("layout width ${layoutParams.width} effectiveWidth $screenWidth")
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

        private fun getTargetScale(imgWidth: Int, imgHeight: Int, displayMode: Int): Float {
            return if (Preferences.Constant.VIEWER_DISPLAY_FILL == displayMode) { // Fill screen
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

            ssiv.setIgnoreTouchEvents(false)
            ssiv.setDirection(CustomSubsamplingScaleImageView.Direction.HORIZONTAL)
            ssiv.setPreferredBitmapConfig(colorDepth)
            ssiv.setDoubleTapZoomDuration(500)
            ssiv.setOnTouchListener(null)

            rootView.minimumHeight = 0
            noImgTxt.isVisible = false
        }

        // == SUBSAMPLINGSCALEVIEW CALLBACKS
        override fun onReady() {
            val scaleView = imgView as CustomSubsamplingScaleImageView
            adjustDimensions(
                0,
                (scaleView.getAbsoluteScale() * scaleView.getSHeight()).toInt(),
                Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation,
                false
            )
            isLoading.set(false) // All that's left is to load tiles => consider the job done already
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
                Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation,
                true
            )
            isLoading.set(false)
            return false
        }
    }
}