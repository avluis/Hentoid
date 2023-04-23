package me.devsaki.hentoid.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.renderscript.RenderScript
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.UnitTransformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.GlideApp
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView
import me.devsaki.hentoid.customssiv.CustomSubsamplingScaleImageView.OnImageEventListener
import me.devsaki.hentoid.customssiv.ImageSource
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.SmartRotateTransformation
import timber.log.Timber
import kotlin.math.roundToInt

class ImagePagerAdapterK(context: Context) :
    ListAdapter<ImageFile, ImagePagerAdapterK.ImageViewHolder>(IMAGE_DIFF_CALLBACK) {

    enum class ViewType(val value: Int) {
        DEFAULT(0), IMAGEVIEW_STRETCH(1), SSIV_VERTICAL(2)
    }

    // Screen width and height; used to adjust dimensions of small images handled by Glide
    val screenWidth = HentoidApp.getInstance().resources.displayMetrics.widthPixels
    val screenHeight = HentoidApp.getInstance().resources.displayMetrics.heightPixels

    private val pageMinHeight =
        HentoidApp.getInstance().resources.getDimension(R.dimen.page_min_height).toInt()

    private var itemTouchListener: OnTouchListener? = null
    private var recyclerView: RecyclerView? = null
    private val initialAbsoluteScales: MutableMap<Int, Float> = HashMap()
    private val absoluteScales: MutableMap<Int, Float> = HashMap()

    // To preload images before they appear on screen with CustomSubsamplingScaleImageView
    private var maxBitmapWidth = -1
    private var maxBitmapHeight = -1

    // Direction user is curently reading the book with
    private var isScrollLTR = true

    // Single instance of RenderScript
    private var rs: RenderScript? = null

    // Cached prefs
    private var separatingBarsHeight = 0
    private var viewerOrientation = 0
    private var displayMode = 0
    private var longTapZoomEnabled = false
    private var autoRotate = false
    private var isSmoothRendering = false
    private var doubleTapZoomCap = 0f


    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) rs = RenderScript.create(context)
    }

    // Book prefs have to be set explicitely because the cached Content linked from each ImageFile
    // might not have the latest properties
    fun refreshPrefs(bookPreferences: Map<String, String>) {
        val separatingBarsPrefs = Preferences.getReaderSeparatingBars()
        separatingBarsHeight = when (separatingBarsPrefs) {
            Preferences.Constant.VIEWER_SEPARATING_BARS_SMALL -> 4
            Preferences.Constant.VIEWER_SEPARATING_BARS_MEDIUM -> 16
            Preferences.Constant.VIEWER_SEPARATING_BARS_LARGE -> 64
            else -> 0
        }
        longTapZoomEnabled = Preferences.isReaderHoldToZoom()
        autoRotate = Preferences.isReaderAutoRotate()
        displayMode = Preferences.getContentDisplayMode(bookPreferences)
        viewerOrientation = Preferences.getContentOrientation(bookPreferences)
        isSmoothRendering = Preferences.isContentSmoothRendering(bookPreferences)
        val doubleTapZoomCapCode = Preferences.getReaderCapTapZoom()
        doubleTapZoomCap =
            if (Preferences.Constant.VIEWER_CAP_TAP_ZOOM_NONE == doubleTapZoomCapCode) -1f else doubleTapZoomCapCode.toFloat()
    }

    fun setRecyclerView(v: RecyclerView?) {
        recyclerView = v
    }

    fun setItemTouchListener(itemTouchListener: OnTouchListener?) {
        this.itemTouchListener = itemTouchListener
    }

    private fun getImageType(img: ImageFile?): Int {
        if (null == img) return IMG_TYPE_OTHER
        val extension = FileHelper.getExtension(img.fileUri)
        if ("gif".equals(extension, ignoreCase = true) || img.mimeType.contains("gif")) {
            return IMG_TYPE_GIF
        }
        if ("apng".equals(extension, ignoreCase = true) || img.mimeType.contains("apng")) {
            return IMG_TYPE_APNG
        }
        return if ("webp".equals(extension, ignoreCase = true) || img.mimeType.contains("webp")) {
            IMG_TYPE_AWEBP
        } else IMG_TYPE_OTHER
    }

    override fun getItemViewType(position: Int): Int {
        if (Preferences.Constant.VIEWER_DISPLAY_STRETCH == displayMode) return ViewType.IMAGEVIEW_STRETCH.value
        return if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewType.SSIV_VERTICAL.value else ViewType.DEFAULT.value
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ImageViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        val view: View = inflater.inflate(R.layout.item_reader_image, viewGroup, false)
        if (ViewType.DEFAULT.value == viewType) {
            // ImageView shouldn't react to click events when in vertical mode (controlled by ZoomableFrame / ZoomableRecyclerView)
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
                val image = view.findViewById<View>(R.id.imageview)
                image.isClickable = false
                image.isFocusable = false
            }
        } else if (ViewType.IMAGEVIEW_STRETCH.value == viewType) {
            val image = view.findViewById<ImageView>(R.id.imageview)
            image.scaleType = ImageView.ScaleType.FIT_XY
        } else if (ViewType.SSIV_VERTICAL.value == viewType) {
            val image = view.findViewById<CustomSubsamplingScaleImageView>(R.id.ssiv)
            image.setIgnoreTouchEvents(true)
            image.setDirection(CustomSubsamplingScaleImageView.Direction.VERTICAL)
        }
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) view.minimumHeight =
            pageMinHeight // Avoid stacking 0-px tall images on screen and load all of them at the same time
        return ImageViewHolder(view)
    }

    // TODO make all that method less ugly
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        Timber.d("Picture %d : BindViewHolder", position)
        val imageType = getImageType(getImageAt(position))
        if (holder.forceImageView != null) { // ImageView has been forced
            holder.switchImageView(holder.forceImageView!!)
            holder.forceImageView = null // Reset force flag
        } else if (IMG_TYPE_GIF == imageType || IMG_TYPE_APNG == imageType)
            holder.switchImageView(true)
        else holder.switchImageView(holder.itemViewType == ViewType.IMAGEVIEW_STRETCH.value)


        // Initialize SSIV when required
        if ((holder.itemViewType == ViewType.DEFAULT.value) && Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation && !holder.isImageView) {
            holder.ssiv.setPreloadDimensions(holder.itemView.width, holder.imgView.height)
            if (!Preferences.isReaderZoomTransitions()) holder.ssiv.setDoubleTapZoomDuration(10)
            holder.ssiv.setOffsetLeftSide(isScrollLTR)
            holder.ssiv.setScaleListener { s: Double ->
                onAbsoluteScaleChanged(position, s)
            }
        }
        val layoutStyle =
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        val layoutParams = holder.imgView.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = layoutStyle
        holder.imgView.layoutParams = layoutParams
        if (Preferences.Constant.VIEWER_ORIENTATION_HORIZONTAL == viewerOrientation) holder.imgView.setOnTouchListener(
            itemTouchListener
        )
        var imageAvailable = true
        val img = getImageAt(position)
        if (img != null && img.fileUri.isNotEmpty()) holder.setImage(img)
        else imageAvailable = false
        val isStreaming = (img != null && !imageAvailable) && img.status == StatusContent.ONLINE
        val isExtracting = img != null && !imageAvailable && !img.url.startsWith("http")
        if (holder.noImgTxt != null) {
            @StringRes var text: Int = R.string.image_not_found
            if (isStreaming) text = R.string.image_streaming else if (isExtracting) text =
                R.string.image_extracting
            holder.noImgTxt.setText(text)
            holder.noImgTxt.visibility = if (!imageAvailable) View.VISIBLE else View.GONE
        }
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        // Set the holder back to its original constraints while in vertical mode
        // (not doing this will cause super high memory usage by trying to load _all_ images)
        if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) {
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
        rs?.destroy()
    }

    fun reset() {
        absoluteScales.clear()
        initialAbsoluteScales.clear()
    }

    fun getDimensionsAtPosition(position: Int): Point {
        if (recyclerView != null) {
            val holder =
                recyclerView!!.findViewHolderForAdapterPosition(position) as ImageViewHolder?
            if (holder != null) return holder.dimensions
        }
        return Point()
    }

    fun getAbsoluteScaleAtPosition(position: Int): Float {
        if (absoluteScales.containsKey(position)) {
            val result = absoluteScales[position]
            if (result != null) return result
        } else if (recyclerView != null) {
            val holder =
                recyclerView!!.findViewHolderForAdapterPosition(position) as ImageViewHolder?
            if (holder != null) return holder.absoluteScale
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
        if (recyclerView != null) {
            val holder =
                recyclerView!!.findViewHolderForAdapterPosition(position) as ImageViewHolder?
            if (holder != null && initialAbsoluteScales.containsKey(position)) {
                val initialScale = initialAbsoluteScales[position]
                if (initialScale != null) holder.absoluteScale = targetRelativeScale * initialScale
            }
        }
    }

    fun resetScaleAtPosition(position: Int) {
        if (recyclerView != null) {
            val holder =
                recyclerView!!.findViewHolderForAdapterPosition(position) as ImageViewHolder?
            holder?.resetScale()
        }
    }

    fun multiplyScale(multiplier: Float) {
        if (recyclerView != null) {
            for (i in 0 until itemCount) {
                val holder = recyclerView!!.findViewHolderForAdapterPosition(i) as ImageViewHolder?
                holder?.multiplyVirtualScale(multiplier)
            }
        }
    }

    private fun onAbsoluteScaleChanged(position: Int, scale: Double) {
        Timber.d(">> position %d -> scale %s", position, scale)
        if (!initialAbsoluteScales.containsKey(position)) initialAbsoluteScales[position] =
            scale.toFloat()
        absoluteScales[position] = scale.toFloat()
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
        val ssiv: CustomSubsamplingScaleImageView = itemView.findViewById(R.id.ssiv)
        private val imageView: ImageView = itemView.findViewById(R.id.imageview)
        val noImgTxt: TextView? = itemView.findViewById(R.id.viewer_no_page_txt)

        lateinit var imgView: View

        var isImageView = false
        var forceImageView: Boolean? = null
        private var img: ImageFile? = null
        private var scaleMultiplier = 1f // When used with ZoomableFrame in vertical mode

        init {
            noImgTxt?.visibility = View.GONE
        }

        fun setImage(img: ImageFile) {
            this.img = img
            val imgType: Int = getImageType(img)
            val uri = Uri.parse(img.fileUri)
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
                if (isSmoothRendering) ssiv.setRenderScript(rs) else ssiv.setRenderScript(null)
                ssiv.setImage(ImageSource.uri(uri))
            } else { // ImageView
                val view = imgView as ImageView
                Timber.d("Using Glide")
                val centerInside: Transformation<Bitmap> = CenterInside()
                val smartRotate90 = if (autoRotate) SmartRotateTransformation(
                    90f, screenWidth, screenHeight
                ) else UnitTransformation.get()
                GlideApp.with(view).load(uri)
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

        fun switchImageView(isImageView: Boolean) {
            Timber.d(
                "Picture %d : switching to %s",
                absoluteAdapterPosition,
                if (isImageView) "imageView" else "ssiv"
            )
            ssiv.isVisible = !isImageView
            imageView.isVisible = isImageView
            imgView = if (isImageView) imageView else ssiv
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
        }

        override fun onTileLoadError(e: Throwable) {
            Timber.d(e, "Picture %d : tileLoad error", absoluteAdapterPosition)
        }

        override fun onPreviewReleased() {
            // Nothing special
        }

        // == GLIDE CALLBACKS
        override fun onLoadFailed(
            e: GlideException?, model: Any, target: Target<Drawable>, isFirstResource: Boolean
        ): Boolean {
            Timber.d(
                e, "Picture %d : Glide loading failed : %s", absoluteAdapterPosition, img!!.fileUri
            )
            if (noImgTxt != null) noImgTxt.visibility = View.VISIBLE
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (Preferences.Constant.VIEWER_ORIENTATION_VERTICAL == viewerOrientation) adjustHeight(
                resource.intrinsicWidth, resource.intrinsicHeight, true
            )
            return false
        }
    }

    companion object {
        // PNGs, JPEGs and WEBPs -> use CustomSubsamplingScaleImageView; will fallback to Glide if animation detected
        const val IMG_TYPE_OTHER = 0

        // Static and animated GIFs -> use APNG4Android library
        const val IMG_TYPE_GIF = 1

        // Animated PNGs -> use APNG4Android library
        const val IMG_TYPE_APNG = 2

        // Animated WEBPs -> use APNG4Android library
        const val IMG_TYPE_AWEBP = 3

        val IMAGE_DIFF_CALLBACK: DiffUtil.ItemCallback<ImageFile> =
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
    }
}