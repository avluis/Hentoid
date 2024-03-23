package me.devsaki.hentoid.fragments.library

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.textfield.TextInputLayout
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.core.WORK_CLOSEABLE
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryTransformBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.determineEncoder
import me.devsaki.hentoid.util.image.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.image.isImageLossless
import me.devsaki.hentoid.util.image.screenHeight
import me.devsaki.hentoid.util.image.screenWidth
import me.devsaki.hentoid.util.image.tintBitmap
import me.devsaki.hentoid.util.image.transform
import me.devsaki.hentoid.viewholders.DrawerItem
import me.devsaki.hentoid.workers.TransformWorker
import okio.use
import timber.log.Timber
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

class LibraryTransformDialogFragment : BaseDialogFragment<LibraryTransformDialogFragment.Parent>() {
    companion object {
        const val KEY_CONTENTS = "contents"

        fun invoke(parent: Fragment, contentList: List<Content>) {
            val args = Bundle()
            args.putLongArray(
                KEY_CONTENTS, contentList.map { c -> c.id }.toLongArray()
            )
            invoke(parent, LibraryTransformDialogFragment(), args)
        }
    }

    // UI
    private var binding: DialogLibraryTransformBinding? = null

    // === VARIABLES
    private lateinit var contentIds: LongArray
    private val content: Content? by lazy {
        val dao = ObjectBoxDAO(requireContext())
        try {
            dao.selectContent(contentIds[contentIndex])
        } finally {
            dao.cleanup()
        }
    }
    private var contentIndex = 0
    private var pageIndex = 0
    private var maxPages = -1
    private val glideRequestOptions: RequestOptions
    private val itemAdapter = ItemAdapter<DrawerItem>()
    private val fastAdapter = FastAdapter.with(itemAdapter)

    init {
        val context: Context = HentoidApp.getInstance()
        val tintColor = ThemeHelper.getColor(context, R.color.light_gray)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_hentoid_trans)
        val d: Drawable = BitmapDrawable(context.resources, tintBitmap(bmp, tintColor))

        val centerInside: Transformation<Bitmap> = CenterInside()
        glideRequestOptions = RequestOptions().optionalTransform(centerInside).error(d)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val contentIdArg = arguments?.getLongArray(KEY_CONTENTS)
        require(!(null == contentIdArg || contentIdArg.isEmpty())) { "No content IDs" }
        contentIds = contentIdArg!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View? {
        binding = DialogLibraryTransformBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        refreshControls(true)
        refreshThumb()

        binding?.apply {
            resizeSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isResizeEnabled = isChecked
                refreshControls()
                refreshThumb()
            }
            resizeMethod.setOnIndexChangeListener { index ->
                Settings.resizeMethod = index
                refreshControls()
                refreshThumb()
            }
            resizeMethod1Ratio.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(resizeMethod1Ratio, 100, 200)) {
                    Settings.resizeMethod1Ratio = value.toInt()
                    refreshThumb()
                }
            }
            resizeMethod2MaxWidth.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(
                        resizeMethod2MaxWidth,
                        screenWidth,
                        screenWidth * 10
                    )
                ) {
                    Settings.resizeMethod2Width = value.toInt()
                    refreshThumb()
                }
            }
            resizeMethod2MaxHeight.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(
                        resizeMethod2MaxHeight,
                        screenHeight,
                        screenHeight * 10
                    )
                ) {
                    Settings.resizeMethod2Height = value.toInt()
                    refreshThumb()
                }
            }
            resizeMethod3Ratio.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(resizeMethod3Ratio, 10, 100)) {
                    Settings.resizeMethod3Ratio = value.toInt()
                    refreshThumb()
                }
            }
            transcodeMethod.setOnIndexChangeListener { index ->
                Settings.transcodeMethod = index
                refreshControls()
                refreshThumb()
            }
            encoderAll.setOnValueChangeListener { value ->
                Settings.transcodeEncoderAll = value.toInt()
                refreshControls()
                refreshThumb()
            }
            encoderLossless.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossless = value.toInt()
                refreshThumb()
            }
            encoderLossy.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossy = value.toInt()
                refreshThumb()
            }
            encoderQuality.editText?.setOnTextChangedListener(lifecycleScope) { value ->
                if (checkRange(encoderQuality, 75, 100)) {
                    Settings.transcodeQuality = value.toInt()
                    refreshThumb()
                }
            }
            prevPageBtn.setOnClickListener {
                if (pageIndex > 0) pageIndex--
                refreshThumb()
            }
            nextPageBtn.setOnClickListener {
                if (pageIndex < maxPages - 1) pageIndex++
                refreshThumb()
            }
            thumb.setOnClickListener {
                preview.isVisible = true
            }
            preview.setOnClickListener {
                preview.isVisible = false
            }
            actionButton.setOnClickListener { onActionClick(buildParams()) }
        }
    }

    private fun refreshControls(applyValues: Boolean = false) {
        binding?.apply {
            val isAiUpscale = (3 == Settings.resizeMethod) && Settings.isResizeEnabled

            // Resize
            if (applyValues) resizeSwitch.isChecked = Settings.isResizeEnabled

            if (applyValues) resizeMethod.index = Settings.resizeMethod
            resizeMethod.isVisible = Settings.isResizeEnabled
            resizeMethod1Ratio.isVisible = (0 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod1Ratio.editText?.setText(Settings.resizeMethod1Ratio.toString())
            resizeMethod2MaxWidth.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) {
                val value = max(Settings.resizeMethod2Width, screenWidth)
                resizeMethod2MaxWidth.editText?.setText(value.toString())
            }
            resizeMethod2MaxHeight.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) {
                val value = max(Settings.resizeMethod2Height, screenHeight)
                resizeMethod2MaxHeight.editText?.setText(value.toString())
            }
            resizeMethod3Ratio.isVisible = (2 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod3Ratio.editText?.setText(Settings.resizeMethod3Ratio.toString())

            // Transcode
            transcodeHeader.isVisible = !isAiUpscale
            transcodeMethod.isVisible = !isAiUpscale
            if (applyValues) transcodeMethod.index = Settings.transcodeMethod
            encoderAll.isVisible = (0 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderAll.value = Settings.transcodeEncoderAll.toString()
            encoderLossless.isVisible = (1 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderLossless.value = Settings.transcodeEncoderLossless.toString()
            encoderLossy.isVisible = (1 == transcodeMethod.index && !isAiUpscale)
            if (applyValues) encoderLossy.value = Settings.transcodeEncoderLossy.toString()
            encoderQuality.isVisible = (1 == transcodeMethod.index
                    || (0 == transcodeMethod.index
                    && (Settings.transcodeEncoderAll == PictureEncoder.JPEG.value || Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSY.value)))
            if (isAiUpscale) encoderQuality.isVisible = false
            if (applyValues) encoderQuality.editText?.setText(Settings.transcodeQuality.toString())

            // Warning list
            warningsList.adapter = fastAdapter

            val encoderWarning = (
                    (0 == transcodeMethod.index && (Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSY.value || Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSLESS.value))
                            || (1 == transcodeMethod.index && (Settings.transcodeEncoderLossy == PictureEncoder.WEBP_LOSSY.value || Settings.transcodeEncoderLossless == PictureEncoder.WEBP_LOSSLESS.value))
                    )

            // Check if content contains transformed pages already
            var retransformWarning = false
            content?.apply {
                retransformWarning = imageList.count { i -> i.isTransformed } > 0
            }

            if (encoderWarning || retransformWarning || isAiUpscale) {
                itemAdapter.clear()
                if (encoderWarning) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.encoder_warning),
                        R.drawable.ic_warning,
                        Content.getWebActivityClass(Site.NONE),
                        1,
                        true
                    )
                )
                if (retransformWarning) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.retransform_warning),
                        R.drawable.ic_warning,
                        Content.getWebActivityClass(Site.NONE),
                        2,
                        true
                    )
                )
                if (isAiUpscale) itemAdapter.add(
                    DrawerItem(
                        resources.getString(R.string.ai_rescale_warning),
                        R.drawable.ic_warning,
                        Content.getWebActivityClass(Site.NONE),
                        2,
                        true
                    )
                )
                warningsList.isVisible = true
            } else warningsList.isVisible = false
        }
    }

    @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
    @SuppressLint("SetTextI18n")
    private fun refreshThumb() {
        val rawSourceBitmap = getCurrentBitmap() ?: return
        val rawData = rawSourceBitmap.second
        val picName = rawSourceBitmap.first

        binding?.previewProgress?.isVisible = true

        lifecycleScope.launch {
            val isLossless = isImageLossless(rawData)
            val sourceSize = FileHelper.formatHumanReadableSize(rawData.size.toLong(), resources)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(rawData, 0, rawData.size, options)
            val sourceDims = Point(options.outWidth, options.outHeight)
            val sourceMime = getMimeTypeFromPictureBinary(rawData)
            val sourceName = picName + "." + FileHelper.getExtensionFromMimeType(sourceMime)
            val params = buildParams()
            val targetData = withContext(Dispatchers.IO) {
                return@withContext transform(rawData, params, true)
            }
            val unchanged = targetData == rawData

            val targetSize = FileHelper.formatHumanReadableSize(targetData.size.toLong(), resources)
            BitmapFactory.decodeByteArray(targetData, 0, targetData.size, options)
            val targetDims = Point(options.outWidth, options.outHeight)
            val targetMime = determineEncoder(isLossless, targetDims, params).mimeType
            val targetName = picName + "." + FileHelper.getExtensionFromMimeType(targetMime)

            binding?.apply {
                if (unchanged) {
                    previewName.text = resources.getText(R.string.transform_unsupported)
                    previewDims.text = "${sourceDims.x} x ${sourceDims.y}"
                    previewSize.text = sourceSize
                } else {
                    previewName.text = "$sourceName ➤ $targetName"
                    previewDims.text =
                        "${sourceDims.x} x ${sourceDims.y} ➤ ${targetDims.x} x ${targetDims.y}"
                    previewSize.text = "$sourceSize ➤ $targetSize"
                }
                Glide.with(thumb).load(targetData).apply(glideRequestOptions).into(thumb)
                Glide.with(preview).load(targetData).apply(glideRequestOptions).into(preview)
                previewProgress.isVisible = false
            }
        }
    }

    private fun getCurrentBitmap(): Pair<String, ByteArray>? {
        content?.apply {
            // Get bitmap for display
            val pages = imageList.filter { i -> i.isReadable }
            maxPages = pages.size
            val page = pages[pageIndex]
            try {
                FileHelper.getInputStream(requireContext(), Uri.parse(page.fileUri)).use {
                    return Pair(page.name, it.readBytes())
                }
            } catch (t: Throwable) {
                Timber.w(t)
            }
        }
        return null
    }

    private fun buildParams(): TransformParams {
        binding!!.apply {
            return TransformParams(
                resizeSwitch.isChecked,
                resizeMethod.index,
                resizeMethod1Ratio.editText!!.text.toString().toInt(),
                resizeMethod2MaxHeight.editText!!.text.toString().toInt(),
                resizeMethod2MaxWidth.editText!!.text.toString().toInt(),
                resizeMethod3Ratio.editText!!.text.toString().toInt(),
                transcodeMethod.index,
                PictureEncoder.fromValue(encoderAll.value.toInt())!!,
                PictureEncoder.fromValue(encoderLossy.value.toInt())!!,
                PictureEncoder.fromValue(encoderLossless.value.toInt())!!,
                encoderQuality.editText!!.text.toString().toInt(),
            )
        }
    }

    private fun onActionClick(params: TransformParams) {
        // Check if no dialog is in error state
        binding?.apply {
            val nbError = container.children
                .filter { c -> c is TextInputLayout }
                .map { c -> c as TextInputLayout }
                .count { til -> til.isErrorEnabled }

            if (nbError > 0) return

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val serializedParams = moshi.adapter(TransformParams::class.java).toJson(params)

            val myData: Data = workDataOf(
                "IDS" to contentIds,
                "PARAMS" to serializedParams
            )

            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueueUniqueWork(
                R.id.transform_service.toString(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequest.Builder(TransformWorker::class.java)
                    .setInputData(myData)
                    .addTag(WORK_CLOSEABLE).build()
            )
        }
        parent?.leaveSelectionMode()
        dismissAllowingStateLoss()
    }

    private fun checkRange(text: TextInputLayout, minValue: Int, maxValue: Int): Boolean {
        val editTxt = text.editText
        require(editTxt != null)
        val errMsg = resources.getString(R.string.range_check, minValue, maxValue)
        val nbMaxDigits = floor(log10(maxValue.toDouble())) + 1
        if (editTxt.text.toString().isEmpty() || editTxt.text.toString().length > nbMaxDigits) {
            text.isErrorEnabled = true
            text.error = errMsg
            return false
        }
        val intValue = editTxt.text.toString().toInt()
        if (intValue < minValue || intValue > maxValue) {
            text.isErrorEnabled = true
            text.error = errMsg
            return false
        }
        text.isErrorEnabled = false
        text.error = null
        return true
    }

    interface Parent {
        fun leaveSelectionMode()
    }
}