package me.devsaki.hentoid.fragments.reader

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.databinding.FragmentReaderPagerBinding
import me.devsaki.hentoid.databinding.IncludeReaderControlsOverlayBinding
import me.devsaki.hentoid.ui.invokeNumberInputDialog
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DIRECTION_LTR
import me.devsaki.hentoid.util.Settings.Value.VIEWER_DIRECTION_RTL
import me.devsaki.hentoid.util.coerceIn
import me.devsaki.hentoid.util.toast
import java.util.Locale

class ReaderNavigation(private val pager: Pager, inBinding: FragmentReaderPagerBinding) {
    // == UI
    private var superBinding: FragmentReaderPagerBinding? = null
    private var binding: IncludeReaderControlsOverlayBinding? = null

    // Bottom bar controls (proxies for left or right position, depending on current reading direction)
    private var pageCurrentNumber: TextView? = null
    private var pageMaxNumber: TextView? = null
    private var prevFunctionalButton: View? = null
    private var nextFunctionalButton: View? = null

    // == VARS
    private var images: List<ImageFile>? = null
    private var chapters: List<Chapter>? = null
    private var currentChapter: Chapter? = null

    // Relative (chapter-scale) max page number
    private var maxPageNumber = 0
    private var isContentDynamic = false

    private var isContentFirst = false
    private var isContentLast = false


    init {
        superBinding = inBinding
        inBinding.controlsOverlay.let {
            binding = it
            it.pageSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    var offset = 0
                    if (Settings.isReaderChapteredNavigation) {
                        getCurrentChapter()?.apply {
                            val chapImgs = readableImageFiles
                            if (chapImgs.isNotEmpty()) offset =
                                pager.indexFromPageNum(chapImgs[0].order)
                        }
                    }
                    pager.seekToIndex(0.coerceAtLeast(offset + value.toInt()))
                }
            }
            it.pageSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    it.imagePreviewLeft.visibility = View.VISIBLE
                    it.imagePreviewCenter.visibility = View.VISIBLE
                    it.imagePreviewRight.visibility = View.VISIBLE
                    superBinding?.recyclerView?.visibility = View.INVISIBLE
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    it.imagePreviewLeft.visibility = View.INVISIBLE
                    it.imagePreviewCenter.visibility = View.INVISIBLE
                    it.imagePreviewRight.visibility = View.INVISIBLE
                    superBinding?.recyclerView?.visibility = View.VISIBLE
                }
            })
        }
    }

    fun clear() {
        binding = null
        superBinding = null
    }

    /**
     * Update the visibility of "next/previous book" buttons
     *
     * @param content Current book
     */
    fun onContentChanged(content: Content) {
        val direction = Settings.getContentDirection(content.bookPreferences)
        nextFunctionalButton =
            if (VIEWER_DIRECTION_LTR == direction) binding?.nextBookBtn else binding?.prevBookBtn
        prevFunctionalButton =
            if (VIEWER_DIRECTION_LTR == direction) binding?.prevBookBtn else binding?.nextBookBtn
        isContentFirst = content.isFirst
        isContentLast = content.isLast
        isContentDynamic = content.isDynamic
    }

    fun onImagesChanged(images: List<ImageFile>) {
        this.images = images
        chapters = images.mapNotNull { obj: ImageFile -> obj.linkedChapter }
            .sortedBy { obj: Chapter -> obj.order }.distinct().toList()

        // Can't access the gallery when there's no page to display
        binding?.apply {
            if (images.isNotEmpty()) galleryBtn.visibility = View.VISIBLE
            else galleryBtn.visibility = View.GONE
        }
        maxPageNumber = images.count { obj: ImageFile -> obj.isReadable }
    }

    private fun onChapterChanged(chapter: Chapter) {
        pageCurrentNumber?.context?.toast(chapter.name)
        updateNextPrevButtonsChapter(chapter)
    }

    private fun updateNextPrevButtonsChapter(chapter: Chapter?) {
        val chapterIndex = getChapterIndex(chapter)
        val isChapterFirst = 0 == chapterIndex
        val isChapterLast = chapters!!.size - 1 == chapterIndex
        prevFunctionalButton?.visibility = if (isChapterFirst) View.INVISIBLE else View.VISIBLE
        nextFunctionalButton?.visibility = if (isChapterLast) View.INVISIBLE else View.VISIBLE
    }

    fun setDirection(direction: Int) {
        binding?.apply {
            if (VIEWER_DIRECTION_LTR == direction) {
                pageCurrentNumber = pagerLeftTxt
                pageMaxNumber = pagerRightTxt
                pageSlider.rotationY = 0f
                prevBookBtn.setOnClickListener { previousContainer() }
                nextBookBtn.setOnClickListener { nextContainer() }
            } else if (VIEWER_DIRECTION_RTL == direction) {
                pageCurrentNumber = pagerRightTxt
                pageMaxNumber = pagerLeftTxt
                pageSlider.rotationY = 180f
                prevBookBtn.setOnClickListener { nextContainer() }
                nextBookBtn.setOnClickListener { previousContainer() }
            }
            pageMaxNumber?.setOnClickListener(null)
            pageCurrentNumber?.setOnClickListener {
                invokeNumberInputDialog(
                    pageSlider.context,
                    R.string.goto_page
                ) { absPageNum: Int ->
                    pager.goToPage(absPageNum)
                }
            }
        }
    }

    /**
     * Update the display of page position controls (text and bar)
     */
    fun updatePageControls() {
        val img = pager.currentImg ?: return
        var pageNum = img.displayOrder + 1
        var pageOffset = 0
        if (Settings.isReaderChapteredNavigation && !isContentDynamic) {
            val newChap = getCurrentChapter()
            if (newChap != null) {
                if (null != currentChapter && newChap.uniqueHash() != currentChapter!!.uniqueHash())
                    onChapterChanged(newChap)
                val chapImgs = newChap.readableImageFiles
                // Caution : ImageFiles inside chapters don't have any displayed order
                // To get it, a mapping between image list and chapters has to be done
                if (chapImgs.isNotEmpty()) pageOffset = chapImgs[0].order
                pageNum = pageNum - pageOffset + 1
            }
            currentChapter = newChap
        } else {
            currentChapter = null
        }
        var maxPageNum = maxPageNumber
        if (Settings.isReaderChapteredNavigation && currentChapter != null) {
            maxPageNum = currentChapter!!.readableImageFiles.size
        }
        pageCurrentNumber?.text = String.format(Locale.ENGLISH, "%d", pageNum)
        pageMaxNumber?.text = String.format(Locale.ENGLISH, "%d", maxPageNum)
        superBinding?.viewerPagenumberText?.text = String.format(
            Locale.ENGLISH,
            "%d / %d",
            pageNum,
            maxPageNum
        )

        // Only update slider when user isn't skimming _with_ the slider
        binding?.let {
            if (!it.imagePreviewCenter.isVisible) {
                val sliderMaxPos = 1.coerceAtLeast(maxPageNum - 1)
                // Next line to avoid setting a max position inferior to current position
                it.pageSlider.value = coerceIn(
                    it.pageSlider.value,
                    0f,
                    sliderMaxPos.toFloat()
                )
                it.pageSlider.valueTo = sliderMaxPos.toFloat()
                val imageIndex = getCurrentImageIndex()
                if (imageIndex > -1) it.pageSlider.value = coerceIn(
                    imageIndex.toFloat(),
                    0f,
                    sliderMaxPos.toFloat()
                )
            }
        }
        if (!Settings.isReaderChapteredNavigation || null == chapters || chapters!!.isEmpty()) {
            prevFunctionalButton?.visibility = if (isContentFirst) View.INVISIBLE else View.VISIBLE
            nextFunctionalButton?.visibility = if (isContentLast) View.INVISIBLE else View.VISIBLE
        } else updateNextPrevButtonsChapter(currentChapter)
    }

    fun previousContainer(): Boolean {
        return if (!Settings.isReaderChapteredNavigation) pager.previousBook()
        else if (!previousChapter()) pager.previousBook()
        else true
    }

    fun nextContainer(): Boolean {
        return if (!Settings.isReaderChapteredNavigation) pager.nextBook()
        else if (!nextChapter()) pager.nextBook()
        else true
    }

    private fun previousChapter(): Boolean {
        val currentChIndex = getCurrentChapterIndex()
        if (currentChIndex > 0) {
            chapters?.let {
                val selectedChapter = it[currentChIndex - 1]
                val chImgs = selectedChapter.readableImageFiles
                if (chImgs.isEmpty()) return false
                pager.goToPage(chImgs[0].order)
                return true
            }
        }
        return false
    }

    private fun nextChapter(): Boolean {
        val currentChIndex = getCurrentChapterIndex()
        chapters?.let {
            if (currentChIndex < it.size - 1) {
                val selectedChapter = it[currentChIndex + 1]
                val chImgs = selectedChapter.readableImageFiles
                if (chImgs.isEmpty()) return false
                pager.goToPage(chImgs[0].order)
                return true
            }
        }
        return false
    }

    private fun getChapterIndex(ch: Chapter?): Int {
        if (null == ch || null == chapters) return -1
        chapters?.let {
            if (it.isEmpty()) return -1
            for (i in it.indices) if (it[i].id == ch.id) return i
        }
        return -1
    }

    private fun getCurrentChapterIndex(): Int {
        return getChapterIndex(getCurrentChapter())
    }

    private fun getCurrentChapter(): Chapter? {
        val startFrom = pager.currentImg ?: return null
        return startFrom.linkedChapter
    }

    private fun getCurrentImageIndex(): Int {
        val currentImg = pager.currentImg
        val currentChapter = getCurrentChapter()
        // Absolute index
        return if (!Settings.isReaderChapteredNavigation || null == currentChapter) {
            indexAmong(currentImg, images)
        } else { // Relative to current chapter
            indexAmong(currentImg, currentChapter.readableImageFiles)
        }
    }

    private fun indexAmong(img: ImageFile?, imgs: List<ImageFile>?): Int {
        if (img != null && imgs != null) {
            for (i in imgs.indices) if (imgs[i].id == img.id) return i
        }
        return -1
    }

    interface Pager {
        fun goToPage(absPageNum: Int)
        fun seekToIndex(absIndex: Int)
        fun indexFromPageNum(pageNum: Int): Int
        fun nextBook(): Boolean
        fun previousBook(): Boolean
        val currentImg: ImageFile?
    }
}