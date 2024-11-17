package me.devsaki.hentoid.fragments.reader

import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.View.OnFocusChangeListener
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.adapters.ImagePagerAdapter
import me.devsaki.hentoid.databinding.FragmentReaderPagerBinding
import me.devsaki.hentoid.databinding.IncludeReaderControlsOverlayBinding
import me.devsaki.hentoid.util.Debouncer
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.VIEWER_ORIENTATION_VERTICAL
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SLIDESHOW_DELAY_05
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SLIDESHOW_DELAY_1
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SLIDESHOW_DELAY_16
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SLIDESHOW_DELAY_4
import me.devsaki.hentoid.util.Settings.Value.VIEWER_SLIDESHOW_DELAY_8
import me.devsaki.hentoid.util.coerceIn
import me.devsaki.hentoid.util.removeLabels
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.widget.ReaderSmoothScroller
import me.devsaki.hentoid.widget.ScrollPositionListener
import java.time.Instant
import java.util.Timer
import kotlin.concurrent.timer

private const val KEY_SLIDESHOW_ON = "slideshow_on"
private const val KEY_SLIDESHOW_REMAINING_MS = "slideshow_remaining_ms"

class ReaderSlideshow(private val pager: Pager, lifecycleScope: LifecycleCoroutineScope) {

    // == UI
    private var controlsOverlay: IncludeReaderControlsOverlayBinding? = null

    lateinit var prefsValues: List<Int>
    lateinit var verticalDelays: Array<String>

    private var slideshowTimer: Timer? = null
    private var slideshowPeriodMs: Long = -1
    private var latestSlideshowTick: Long = -1
    private var isSlideshowActive = false

    // Debouncer for the slideshow slider
    private val slideshowSliderDebouncer: Debouncer<Int>

    init {
        slideshowSliderDebouncer = Debouncer(lifecycleScope, 2500) { sliderIndex ->
            onSlideShowSliderChosen(sliderIndex)
        }
    }

    fun init(binding: FragmentReaderPagerBinding, resources: Resources) {
        prefsValues = resources.getStringArray(R.array.pref_viewer_slideshow_delay_values)
            .map { it.toInt() }
        verticalDelays =
            resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries_vertical)

        controlsOverlay = binding.controlsOverlay
        val slider = binding.controlsOverlay.slideshowDelaySlider
        slider.valueFrom = 0f
        val sliderValue = if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation)
            convertPrefsDelayToSliderPosition(Settings.readerSlideshowDelayVertical)
        else convertPrefsDelayToSliderPosition(Settings.readerSlideshowDelay)
        var nbEntries =
            resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries).size
        nbEntries = 1.coerceAtLeast(nbEntries - 1)

        // TODO at some point we'd need to better synch images and book loading to avoid that
        slider.value = coerceIn(sliderValue.toFloat(), 0f, nbEntries.toFloat())
        slider.valueTo = nbEntries.toFloat()
        slider.setLabelFormatter { value: Float ->
            val entries: Array<String> =
                if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation) {
                    resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries_vertical)
                } else {
                    resources.getStringArray(R.array.pref_viewer_slideshow_delay_entries)
                }
            entries[value.toInt()]
        }
        slider.onFocusChangeListener =
            OnFocusChangeListener { _: View?, hasFocus: Boolean ->
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
    }

    fun clear() {
        slideshowSliderDebouncer.clear()
        controlsOverlay = null
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_SLIDESHOW_ON, isSlideshowActive)
        val currentSlideshowSeconds = Instant.now().toEpochMilli() - latestSlideshowTick
        outState.putLong(KEY_SLIDESHOW_REMAINING_MS, slideshowPeriodMs - currentSlideshowSeconds)
    }

    fun onViewStateRestored(savedInstanceState: Bundle) {
        if (savedInstanceState.getBoolean(KEY_SLIDESHOW_ON, false)) {
            startSlideshow(false, savedInstanceState.getLong(KEY_SLIDESHOW_REMAINING_MS))
        }
    }

    fun showUI() {
        var startIndex =
            if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation) Settings.readerSlideshowDelayVertical
            else Settings.readerSlideshowDelay
        startIndex = convertPrefsDelayToSliderPosition(startIndex)
        controlsOverlay?.apply {
            slideshowDelaySlider.value = startIndex.toFloat()
            slideshowDelaySlider.labelBehavior = LabelFormatter.LABEL_FLOATING
            slideshowDelaySlider.visibility = View.VISIBLE
        }
        slideshowSliderDebouncer.submit(startIndex)
    }

    private fun onSlideShowSliderChosen(sliderIndex: Int) {
        val prefsDelay = convertSliderPositionToPrefsDelay(sliderIndex)

        if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation)
            Settings.readerSlideshowDelayVertical = prefsDelay
        else Settings.readerSlideshowDelay = prefsDelay

        controlsOverlay?.apply {
            removeLabels(slideshowDelaySlider)
            slideshowDelaySlider.visibility = View.GONE
        }
        startSlideshow(true, -1)
    }

    fun cancel() {
        slideshowTimer?.cancel()
    }

    fun isActive(): Boolean {
        return isSlideshowActive
    }

    fun stop() {
        if (slideshowTimer != null) {
            slideshowTimer?.cancel()
            slideshowTimer = null
        } else {
            // Mandatory; if we don't recreate it, we can't change scrolling speed as it is cached internally
            val smoothScroller = ReaderSmoothScroller(controlsOverlay!!.root.context)
            smoothScroller.apply {
                setCurrentPositionY(pager.scrollListener.totalScrolledY)
                targetPosition = pager.layoutMgr.findFirstVisibleItemPosition()
                    .coerceAtLeast(pager.layoutMgr.findFirstCompletelyVisibleItemPosition())
            }
            pager.setAndStartSmoothScroll(smoothScroller)
        }
        isSlideshowActive = false
        pager.scrollListener.enableScroll()
        toast(R.string.slideshow_stop)
    }

    private fun startSlideshow(showToast: Boolean, initialDelayMs: Long) {
        // Hide UI
        pager.hideControlsOverlay()

        // Compute slideshow delay
        val delayPref = if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation)
            Settings.readerSlideshowDelayVertical
        else Settings.readerSlideshowDelay

        val factor : Float = when (delayPref) {
            VIEWER_SLIDESHOW_DELAY_05 -> 0.5f
            VIEWER_SLIDESHOW_DELAY_1 -> 1f
            VIEWER_SLIDESHOW_DELAY_4 -> 4f
            VIEWER_SLIDESHOW_DELAY_8 -> 8f
            VIEWER_SLIDESHOW_DELAY_16 -> 16f
            else -> 2f
        }
        if (showToast) {
            if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation)
                toast(
                    R.string.slideshow_start_vertical,
                    verticalDelays[convertPrefsDelayToSliderPosition(delayPref)]
                ) else toast(R.string.slideshow_start, factor)
        }
        pager.scrollListener.disableScroll()
        if (VIEWER_ORIENTATION_VERTICAL == pager.displayParams?.orientation) {
            // Mandatory; if we don't recreate it, we can't change scrolling speed as it is cached internally
            val smoothScroller = ReaderSmoothScroller(controlsOverlay!!.root.context)
            smoothScroller.apply {
                setCurrentPositionY(pager.scrollListener.totalScrolledY)
                setItemHeight(pager.adapter.getDimensionsAtPosition(pager.absImageIndex).y)
                targetPosition = pager.adapter.itemCount - 1
                setSpeed(900f / (factor / 4f))
            }
            pager.setAndStartSmoothScroll(smoothScroller)
        } else {
            slideshowPeriodMs = (factor * 1000).toLong()
            val initialDelayFinal =
                if (initialDelayMs > -1) initialDelayMs else slideshowPeriodMs
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
        if (!pager.nextPage() && Settings.readerSlideshowLoop > 0) {
            if (!Settings.isReaderContinuous) pager.seekToIndex(0)
            else pager.goToBookSelectionStart()
        }
    }

    private fun convertPrefsDelayToSliderPosition(prefsDelay: Int): Int {
        for (i in prefsValues.indices) if (prefsValues[i] == prefsDelay) return i
        return 0
    }

    private fun convertSliderPositionToPrefsDelay(sliderPosition: Int): Int {
        return prefsValues[sliderPosition]
    }

    private fun toast(@StringRes resId: Int, vararg args: Any) {
        controlsOverlay?.root?.context?.toast(resId, *args)
    }

    interface Pager {
        fun nextPage() : Boolean
        fun previousPage() : Boolean
        fun setAndStartSmoothScroll(s: ReaderSmoothScroller)
        fun hideControlsOverlay()
        fun seekToIndex(absIndex: Int)
        fun goToBookSelectionStart()
        val displayParams: ReaderPagerFragment.DisplayParams?
        val scrollListener: ScrollPositionListener
        val layoutMgr: LinearLayoutManager
        val adapter: ImagePagerAdapter
        val absImageIndex: Int
    }
}