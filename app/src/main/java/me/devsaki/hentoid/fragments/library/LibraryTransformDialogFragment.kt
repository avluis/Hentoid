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
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.annimon.stream.Stream
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryTransformBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.ImageHelper
import me.devsaki.hentoid.util.image.ImageTransform
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import okio.use

class LibraryTransformDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogLibraryTransformBinding? = null
    private val binding get() = _binding!!

    // Text listener debouncers
    // TODO

    // === VARIABLES
    private lateinit var contentIds: LongArray
    private var contentIndex = 0
    private var pageIndex = 0
    private val glideRequestOptions: RequestOptions

    init {
        val context: Context = HentoidApp.getInstance()
        val tintColor = ThemeHelper.getColor(context, R.color.light_gray)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_hentoid_trans)
        val d: Drawable = BitmapDrawable(context.resources, ImageHelper.tintBitmap(bmp, tintColor))

        val centerInside: Transformation<Bitmap> = CenterInside()
        glideRequestOptions = RequestOptions().optionalTransform(centerInside).error(d)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val contents = arguments?.getLongArray(KEY_CONTENTS)
        require(!(null == contents || contents.isEmpty())) { "No content IDs" }
        contentIds = contents!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _binding = DialogLibraryTransformBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        refreshControls(true)
        refreshPreview()

        binding.apply {
            resizeSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isResizeEnabled = isChecked
                refreshControls()
                refreshPreview()
            }
            resizeMethod.setOnIndexChangeListener { index ->
                Settings.resizeMethod = index
                refreshControls()
                refreshPreview()
            }
            // TODO apply a debouncer here
            resizeMethod1Ratio.editText?.setOnTextChangedListener { value ->
                if (value.isNotEmpty()) {
                    Settings.resizeMethod1Ratio = value.toInt()
                    refreshPreview()
                }
            }
            // TODO apply a debouncer here
            resizeMethod2MaxWidth.editText?.setOnTextChangedListener { value ->
                if (value.isNotEmpty()) {
                    Settings.resizeMethod2Width = value.toInt()
                    refreshPreview()
                }
            }
            // TODO apply a debouncer here
            resizeMethod2MaxHeight.editText?.setOnTextChangedListener { value ->
                if (value.isNotEmpty()) {
                    Settings.resizeMethod2Height = value.toInt()
                    refreshPreview()
                }
            }
            // TODO apply a debouncer here
            resizeMethod3Ratio.editText?.setOnTextChangedListener { value ->
                if (value.isNotEmpty()) {
                    Settings.resizeMethod3Ratio = value.toInt()
                    refreshPreview()
                }
            }
            transcodeMethod.setOnIndexChangeListener { index ->
                Settings.transcodeMethod = index
                refreshControls()
                refreshPreview()
            }
            encoderAll.setOnValueChangeListener { value ->
                Settings.transcodeEncoderAll = value.toInt()
                refreshControls()
                refreshPreview()
            }
            encoderLossless.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossless = value.toInt()
                refreshPreview()
            }
            encoderLossy.setOnValueChangeListener { value ->
                Settings.transcodeEncoderLossy = value.toInt()
                refreshPreview()
            }
            // TODO apply a debouncer here
            encoderQuality.editText?.setOnTextChangedListener { value ->
                if (value.isNotEmpty()) {
                    Settings.transcodeQuality = value.toInt()
                    refreshPreview()
                }
            }
        }
    }

    private fun refreshControls(applyValues: Boolean = false) {
        binding.apply {
            if (applyValues) resizeSwitch.isChecked = Settings.isResizeEnabled

            if (applyValues) resizeMethod.index = Settings.resizeMethod
            resizeMethod.isVisible = Settings.isResizeEnabled
            resizeMethod1Ratio.isVisible = (0 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod1Ratio.editText?.setText(Settings.resizeMethod1Ratio.toString())
            resizeMethod2MaxWidth.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod2MaxWidth.editText?.setText(Settings.resizeMethod2Width.toString())
            resizeMethod2MaxHeight.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod2MaxHeight.editText?.setText(Settings.resizeMethod2Height.toString())
            resizeMethod3Ratio.isVisible = (2 == resizeMethod.index && resizeMethod.isVisible)
            if (applyValues) resizeMethod3Ratio.editText?.setText(Settings.resizeMethod3Ratio.toString())

            if (applyValues) transcodeMethod.index = Settings.transcodeMethod
            encoderAll.isVisible = (0 == transcodeMethod.index)
            if (applyValues) encoderAll.value = Settings.transcodeEncoderAll.toString()
            encoderLossless.isVisible = (1 == transcodeMethod.index)
            if (applyValues) encoderLossless.value = Settings.transcodeEncoderLossless.toString()
            encoderLossy.isVisible = (1 == transcodeMethod.index)
            if (applyValues) encoderLossy.value = Settings.transcodeEncoderLossy.toString()
            encoderQuality.isVisible = (1 == transcodeMethod.index
                    || (
                    0 == transcodeMethod.index
                            && (Settings.transcodeEncoderAll == PictureEncoder.JPEG.value || Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSY.value)
                    )
                    )
            if (applyValues) encoderQuality.editText?.setText(Settings.transcodeQuality.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshPreview() {
        val rawSourceBitmap = getCurrentBitmap() ?: return
        val rawData = rawSourceBitmap.second
        val picName = rawSourceBitmap.first

        lifecycleScope.launch {
            val isLossless = ImageHelper.isImageLossless(rawData)
            val sourceSize = FileHelper.formatHumanReadableSize(rawData.size.toLong(), resources)
            val sourceBitmap = BitmapFactory.decodeByteArray(rawData, 0, rawData.size)
            val sourceDims = Point(sourceBitmap.width, sourceBitmap.height)
            sourceBitmap.recycle()
            val sourceMime = ImageHelper.getMimeTypeFromPictureBinary(rawData)
            val sourceName = picName + "." + FileHelper.getExtensionFromMimeType(sourceMime)
            val params = buildParams()
            val targetData = withContext(Dispatchers.IO) {
                return@withContext ImageTransform.transform(rawData, params)
            }
            val targetSize = FileHelper.formatHumanReadableSize(targetData.size.toLong(), resources)
            val targetBitmap = BitmapFactory.decodeByteArray(targetData, 0, targetData.size)
            val targetDims = Point(targetBitmap.width, targetBitmap.height)
            val targetMime = ImageTransform.determineEncoder(isLossless, params).mimeType
            val targetName =
                picName + "." + FileHelper.getExtensionFromMimeType(targetMime)

            binding.apply {
                previewName.text = "$sourceName ➤ $targetName"
                previewDims.text =
                    "${sourceDims.x} x ${sourceDims.y} ➤ ${targetDims.x} x ${targetDims.y}"
                previewSize.text = "$sourceSize ➤ $targetSize"
                // TODO zoom on tap
                // TODO buttons
                Glide.with(thumb).load(targetBitmap).apply(glideRequestOptions).into(thumb)
            }
        }
    }

    private fun getCurrentBitmap(): Pair<String, ByteArray>? {
        val dao = ObjectBoxDAO(requireContext())
        try {
            val content = dao.selectContent(contentIds[contentIndex])
            if (content != null) {
                val page = content.imageList[pageIndex]
                FileHelper.getInputStream(requireContext(), Uri.parse(page.fileUri)).use {
                    return Pair(page.name, it.readBytes())
                }
            }
        } finally {
            dao.cleanup()
        }
        return null
    }

    private fun buildParams(): ImageTransform.Params {
        binding.apply {
            return ImageTransform.Params(
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

    private fun onActionClick() {
        val vmFactory = ViewModelFactory(requireActivity().application)
        val viewModel =
            ViewModelProvider(requireActivity(), vmFactory)[LibraryViewModel::class.java]

    }

    companion object {

        const val KEY_CONTENTS = "contents"

        fun invoke(parent: Fragment, contentList: List<Content>) {
            val fragment = LibraryTransformDialogFragment()
            val args = Bundle()
            args.putLongArray(
                KEY_CONTENTS, Helper.getPrimitiveArrayFromList(
                    Stream.of(contentList).map { c -> c.id }.toList()
                )
            )
            fragment.arguments = args
            fragment.show(parent.childFragmentManager, null)
        }
    }
}