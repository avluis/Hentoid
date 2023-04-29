package me.devsaki.hentoid.fragments.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.annimon.stream.Stream
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.RequestOptions
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogLibraryTransformBinding
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.ImageHelperK
import me.devsaki.hentoid.viewmodels.LibraryViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import okio.use

class LibraryTransformDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogLibraryTransformBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private lateinit var contentIds: LongArray
    private var contentIndex = 0
    private var pageIndex = 0
    private val glideRequestOptions: RequestOptions

    init {
        val context: Context = HentoidApp.getInstance()
        val tintColor = ThemeHelper.getColor(context, R.color.light_gray)

        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.ic_hentoid_trans)
        val d: Drawable = BitmapDrawable(context.resources, ImageHelperK.tintBitmap(bmp, tintColor))

        val centerInside: Transformation<Bitmap> = CenterInside()
        glideRequestOptions = RequestOptions()
            .optionalTransform(centerInside)
            .error(d)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        val contents = arguments?.getLongArray(KEY_CONTENTS)
        require(!(null == contents || contents.isEmpty())) { "No content IDs" }
        contentIds = contents!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
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

        refreshControls()
        refreshPreview()

        binding.apply {
            resizeSwitch.setOnCheckedChangeListener { _, isChecked ->
                Settings.isResizeEnabled = isChecked
                refreshControls()
            }
            resizeMethod.setOnIndexChangeListener { index ->
                Settings.resizeMethod = index
                refreshControls()
            }
            transcodeMethod.setOnIndexChangeListener { index ->
                Settings.transcodeMethod = index
                refreshControls()
            }
            encoderAll.setOnIndexChangeListener { index ->
                Settings.transcodeEncoderAll = index
                refreshControls()
            }
        }
    }

    private fun refreshControls() {
        binding.apply {
            resizeSwitch.isChecked = Settings.isResizeEnabled

            resizeMethod.index = Settings.resizeMethod
            resizeMethod.isVisible = Settings.isResizeEnabled
            resizeMethod1Ratio.isVisible = (0 == resizeMethod.index && resizeMethod.isVisible)
            resizeMethod1Ratio.editText?.setText(Settings.resizeMethod1Ratio.toString())
            resizeMethod2MaxWidth.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            resizeMethod2MaxWidth.editText?.setText(Settings.resizeMethod2Width.toString())
            resizeMethod2MaxHeight.isVisible = (1 == resizeMethod.index && resizeMethod.isVisible)
            resizeMethod2MaxHeight.editText?.setText(Settings.resizeMethod2Height.toString())
            resizeMethod3Ratio.isVisible = (2 == resizeMethod.index && resizeMethod.isVisible)
            resizeMethod3Ratio.editText?.setText(Settings.resizeMethod3Ratio.toString())

            transcodeMethod.index = Settings.transcodeMethod
            encoderAll.isVisible = (0 == transcodeMethod.index)
            encoderAll.index = Settings.transcodeEncoderAll // TODO adjust
            encoderLossless.isVisible = (1 == transcodeMethod.index)
            encoderLossless.index = Settings.transcodeEncoderLossless // TODO adjust
            encoderLossy.isVisible = (1 == transcodeMethod.index)
            encoderLossy.index = Settings.transcodeEncoderLossy // TODO adjust
            encoderQuality.isVisible = (1 == transcodeMethod.index
                    || (0 == transcodeMethod.index && (Settings.transcodeEncoderAll == PictureEncoder.JPEG.value || Settings.transcodeEncoderAll == PictureEncoder.WEBP_LOSSY.value))
                    )
            encoderQuality.editText?.setText(Settings.transcodeQuality.toString())
        }
    }

    private fun refreshPreview() {
        val bitmap = getCurrentBitmap() ?: return
        binding.apply {
            // TODO compute and display original and resized bitmap stats
            Glide.with(thumb)
                .load(bitmap)
                .apply(glideRequestOptions)
                .into(thumb)
        }
    }

    private fun getCurrentBitmap(): Bitmap? {
        val dao = ObjectBoxDAO(requireContext())
        try {
            val content = dao.selectContent(contentIds[contentIndex])
            if (content != null) {
                val page = content.imageList[pageIndex]
                FileHelper.getInputStream(requireContext(), Uri.parse(page.fileUri)).use {
                    return BitmapFactory.decodeStream(it)
                }
            }
        } finally {
            dao.cleanup()
        }
        return null
    }

    private fun onActionClick() {
        // TODO Save all to settings
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