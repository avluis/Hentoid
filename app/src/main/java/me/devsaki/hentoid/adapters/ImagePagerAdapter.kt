package me.devsaki.hentoid.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.UnitTransformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Runnable
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.OnImageEventListener
import me.devsaki.hentoid.customssiv.ImageSource
import me.devsaki.hentoid.customssiv.exception.UnsupportedContentException
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.database.reach
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.fragments.reader.ReaderPagerFragment
import me.devsaki.hentoid.gles_renderer.GPUImage
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.image.SmartRotateTransformation
import me.devsaki.hentoid.util.image.screenHeight
import me.devsaki.hentoid.util.image.screenWidth
import me.devsaki.hentoid.views.ZoomableRecyclerView
import me.devsaki.hentoid.widget.OnZoneTapListener
import org.apache.commons.collections4.map.HashedMap
import timber.log.Timber
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

class ImagePagerAdapter(val context: Context) :
    ListAdapter<ImageFile, ImagePagerAdapter.ImageViewHolder>(IMAGE_DIFF_CALLBACK) {

    enum class ImageType(val value: Int) {
        IMG_TYPE_OTHER(0), // PNGs, JPEGs and WEBPs -> use CustomSubsamplingScaleImageView; will fallback to Glide if animation detected
        IMG_TYPE_GIF(1), // Static and animated GIFs -> use APNG4Android library
        IMG_TYPE_APNG(2), // Animated PNGs -> use APNG4Android library
        IMG_TYPE_AWEBP(3) // Animated WEBPs -> use APNG4Android library
    }

    enum class ViewType(val value: Int) {
        DEFAULT(0), IMAGEVIEW_STRETCH(1), SSIV_VERTICAL(2)
    }

    private val pageMinHeight = context.resources.getDimension(R.dimen.page_min_height).toInt()

    private var itemTouchListener: OnZoneTapListener? = null
    private var scaleListener: BiConsumer<Int, Float>? = null
    private var animationAlertListener: Runnable? = null

    private var recyclerView: ZoomableRecyclerView? = null
    private val initialAbsoluteScales: MutableMap<Int, Float> = HashedMap()
    private val absoluteScales: MutableMap<Int, Float> = HashedMap()
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
            if (0 == Settings.colorDepth) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    }

    fun setRecyclerView(v: ZoomableRecyclerView?) {
        recyclerView = v
    }

    fun setItemTouchListener(itemTouchListener: OnZoneTapListener?) {
        this.itemTouchListener = itemTouchListener
    }

    fun setAnimationAlertListener(animationAlertListener: Runnable) {
        this.animationAlertListener = animationAlertListener
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
        val view: View = inflater.inflate(R.layout.item_reader_image, viewGroup, false)
        return ImageViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun reset(holder: ImageViewHolder) {
        holder.apply {
            val image = rootView.findViewById<ImageView>(R.id.imageview)
            image.isClickable = true
            image.isFocusable = true
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setOnTouchListener(null)

            val ssiv = rootView.findViewById<CustomSubsamplingScaleImageView>(R.id.ssiv)
            ssiv.setIgnoreTouchEvents(false)
            ssiv.setDirection(CustomSubsamplingScaleImageView.Direction.HORIZONTAL)
            ssiv.preferredBitmapConfig = colorDepth
            ssiv.setDoubleTapZoomDuration(500)
            ssiv.setOnTouchListener(null)

            rootView.minimumHeight = 0
            noImgTxt.isVisible = false
        }
    }

    // TODO make all that method less ugly
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        Timber.d("Picture %d : BindViewHolder", position)

        val displayParams = getDisplayParamsForPosition(position) ?: return
        val imgViewType = getImageViewType(displayParams)

        reset(holder)

        holder.apply {
            val imageView = rootView.findViewById<ImageView>(R.id.imageview)
            viewerOrientation = displayParams.orientation
            displayMode = displayParams.displayMode
            isSmoothRendering = displayParams.isSmoothRendering

            if (ViewType.DEFAULT == imgViewType) {
                // ImageView shouldn't react to click events when in vertical mode (controlled by ZoomableFrame / ZoomableRecyclerView)
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                    imageView.isClickable = false
                    imageView.isFocusable = false
                }
            } else if (ViewType.IMAGEVIEW_STRETCH == imgViewType) {
                imageView.scaleType = ImageView.ScaleType.FIT_XY
            } else if (ViewType.SSIV_VERTICAL == imgViewType) {
                val ssiv = rootView.findViewById<CustomSubsamplingScaleImageView>(R.id.ssiv)
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
                animationAlertListener?.run()
                switchImageView(isImageView = true, isClickThrough = true)
            } else switchImageView(imgViewType == ViewType.IMAGEVIEW_STRETCH)

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
            val layoutStyle =
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            val layoutParams = imgView.layoutParams
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = layoutStyle
            imgView.layoutParams = layoutParams

            // ImageView or vertical mode => ZoomableRecycleView handles gestures
            if (isImageView || Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                recyclerView?.setTapListener(itemTouchListener)
                imgView.setOnTouchListener(null)
            } else { // Horizontal SSIV => SSIV handles gestures
                recyclerView?.setTapListener(null)
                imgView.setOnTouchListener(itemTouchListener)
            }


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
        if (!holder.isImageView) holder.ssiv.clear()
        super.onViewRecycled(holder)
    }

    fun getImageAt(index: Int): ImageFile? {
        return if (index in 0 until itemCount) getItem(index) else null
    }

    fun destroy() {
        if (isGlInit) glEsRenderer.clear()
        scaleListener = null
        animationAlertListener = null
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
                Preferences.isContentSmoothRendering(bookPreferences),
                false
            )
        }
        return null
    }

    fun getDimensionsAtPosition(position: Int): Point {
        (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.let { holder ->
            return holder.dimensions
        }
        return Point()
    }

    fun getAbsoluteScaleAtPosition(position: Int): Float {
        if (absoluteScales.containsKey(position)) {
            val result = absoluteScales[position]
            if (result != null) return result
        } else (recyclerView?.findViewHolderForAdapterPosition(position) as ImageViewHolder?)?.let { holder ->
            return holder.absoluteScale
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


    inner class ImageViewHolder(val rootView: View) : RecyclerView.ViewHolder(rootView),
        OnImageEventListener, RequestListener<Drawable> {
        val ssiv: CustomSubsamplingScaleImageView = itemView.requireById(R.id.ssiv)
        private val imageView: ImageView = itemView.requireById(R.id.imageview)
        val noImgTxt: TextView = itemView.requireById(R.id.viewer_no_page_txt)

        lateinit var imgView: View

        var displayMode: Int = 0
        var viewerOrientation: Int = 0
        var isSmoothRendering: Boolean = false

        var isImageView = false
        var forceImageView: Boolean? = null
        private var img: ImageFile? = null
        private var scaleMultiplier = 1f // When used with ZoomableFrame in vertical mode

        fun setImage(img: ImageFile) {
            this.img = img
            val imgType = getImageType(img)
            val uri = Uri.parse(img.fileUri)
            noImgTxt.isVisible = false
            Timber.d("Picture %d : binding viewholder %s %s", absoluteAdapterPosition, imgType, uri)
            if (!isImageView) { // SubsamplingScaleImageView
                Timber.d("Using SSIV")
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
                ssiv.setImage(ImageSource.uri(uri))
            } else { // ImageView
                val view = imgView as ImageView
                Timber.d("Using Glide")
                val centerInside: Transformation<Bitmap> = CenterInside()
                val smartRotate90 = if (autoRotate) SmartRotateTransformation(
                    90f, screenWidth, screenHeight
                ) else UnitTransformation.get()
                Glide.with(view).load(uri)
                    .optionalTransform(MultiTransformation(centerInside, smartRotate90))
                    .listener(this).into(view)
            }
        }

        private val scaleType: Int
            get() = if (Preferences.Constant.VIEWER_DISPLAY_FILL == displayMode) {
                CustomSubsamplingScaleImageView.ScaleType.SMART_FILL
            } else {
                CustomSubsamplingScaleImageView.ScaleType.CENTER_INSIDE
            }

        // ImageView
        // TODO doesn't work for Glide as it doesn't use ImageView's scaling
        var absoluteScale: Float
            get() {
                return if (!isImageView) {
                    ssiv.virtualScale
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
                if (ssiv.isImageLoaded && ssiv.isReady && ssiv.isLaidOut) {
                    scaleMultiplier = 0f
                    ssiv.resetScale()
                }
            }
        }

        fun multiplyVirtualScale(multiplier: Float) {
            if (!isImageView) {
                if (ssiv.isImageLoaded && ssiv.isReady && ssiv.isLaidOut) {
                    val rawScale = ssiv.virtualScale / scaleMultiplier
                    ssiv.virtualScale = rawScale * multiplier
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

        private fun adjustHeight(imgWidth: Int, imgHeight: Int, resizeSmallPics: Boolean) {
            val rootLayoutStyle =
                if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
            val layoutParams = rootView.layoutParams
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = rootLayoutStyle
            rootView.layoutParams = layoutParams
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
            val targetHeight: Int = targetImgHeight + separatingBarsHeight
            rootView.minimumHeight = targetHeight
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

        fun switchImageView(isImageView: Boolean, isClickThrough: Boolean = false) {
            Timber.d(
                "Picture %d : switching to %s",
                absoluteAdapterPosition,
                if (isImageView) "imageView" else "ssiv"
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

        // == SUBSAMPLINGSCALEVIEW CALLBACKS
        override fun onReady() {
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                val scaleView = imgView as CustomSubsamplingScaleImageView
                adjustHeight(0, (scaleView.absoluteScale * scaleView.sHeight).toInt(), false)
            }
        }

        override fun onImageLoaded() {
            // Nothing special
        }

        override fun onPreviewLoadError(e: Throwable) {
            // Nothing special
        }

        override fun onImageLoadError(e: Throwable) {
            Timber.d(
                e,
                "Picture %d : SSIV loading failed; reloading with Glide : %s",
                absoluteAdapterPosition,
                img!!.fileUri
            )
            // Fall back to Glide
            forceImageView()
            // Reload adapter
            notifyItemChanged(layoutPosition)
            // Notify the listener of the presence of an animation
            if (e is UnsupportedContentException) animationAlertListener?.run()
        }

        override fun onTileLoadError(e: Throwable) {
            Timber.d(e, "Picture %d : tileLoad error", absoluteAdapterPosition)
        }

        override fun onPreviewReleased() {
            // Nothing special
        }

        // == GLIDE CALLBACKS
        override fun onLoadFailed(
            e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
        ): Boolean {
            Timber.d(
                e, "Picture %d : Glide loading failed : %s", absoluteAdapterPosition, img!!.fileUri
            )
            if (isImageView) noImgTxt.visibility = View.VISIBLE
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            noImgTxt.visibility = View.GONE
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) adjustHeight(
                resource.intrinsicWidth,
                resource.intrinsicHeight,
                true
            )
            return false
        }
    }
}