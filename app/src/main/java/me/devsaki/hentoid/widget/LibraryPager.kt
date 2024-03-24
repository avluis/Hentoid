package me.devsaki.hentoid.widget

import android.view.View
import android.view.View.OnTouchListener
import androidx.core.view.ViewCompat
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.skydoves.balloon.ArrowOrientation
import me.devsaki.hentoid.R
import me.devsaki.hentoid.ui.CarouselDecorator
import me.devsaki.hentoid.ui.invokeNumberInputDialog
import me.devsaki.hentoid.util.showTooltip

/**
 * Page navigation bar for the library screen's paged mode
 */
class LibraryPager(private val onPageChangeListener: Runnable) {

    // == COMMUNICATION

    // == UI
    // Bottom panel with page controls
    private lateinit var pagerPanel: View
    private lateinit var pageCarousel: RecyclerView

    // Decorator for the page number carousel
    private lateinit var decorator: CarouselDecorator

    // == VARIABLES
    private var currentPageNumber = 1
    private var pageCount = 0


    /**
     * Initialize the components of the pager UI
     *
     * @param rootView Root view of the library screen
     */
    fun initUI(rootView: View) {
        pagerPanel = ViewCompat.requireViewById(rootView, R.id.library_pager_group)
        pageCarousel = ViewCompat.requireViewById(rootView, R.id.pager_pageCarousel)
        pageCarousel.setHasFixedSize(true)
        val tapListener: OnTouchListener =
            OnZoneTapListener(pageCarousel, 1).setOnMiddleZoneTapListener { onCarouselClick() }
        decorator = CarouselDecorator(rootView.context, R.layout.item_pagecarousel)
        decorator.decorate(pageCarousel)
        decorator.setOnPageChangeListener { newPageNumber: Int ->
            pageChanged(newPageNumber)
        }
        decorator.setTouchListener(tapListener)
        ViewCompat.requireViewById<View>(rootView, R.id.pager_btnPrevious)
            .setOnClickListener { previousPage() }
        ViewCompat.requireViewById<View>(rootView, R.id.pager_btnNext)
            .setOnClickListener { nextPage() }
    }

    fun show() {
        pagerPanel.visibility = View.VISIBLE
    }

    fun hide() {
        pagerPanel.visibility = View.GONE
    }

    fun isVisible(): Boolean {
        return View.VISIBLE == pagerPanel.visibility
    }

    /**
     * Set the page count
     *
     * @param pageCount Page count (max page number) to be set
     */
    fun setPageCount(pageCount: Int) {
        decorator.setPageCount(pageCount)
        this.pageCount = pageCount
    }

    /**
     * Set the current page number
     *
     * @param page Current page number to be set
     */
    fun setCurrentPage(page: Int) {
        decorator.setCurrentPage(page)
        currentPageNumber = page
    }

    /**
     * Try to get to the next page if it exists
     */
    private fun nextPage() {
        if (currentPageNumber < pageCount) {
            currentPageNumber++
            decorator.setCurrentPage(currentPageNumber)
            onPageChangeListener.run()
        }
    }

    /**
     * Try to get to the previous page if it exists
     */
    private fun previousPage() {
        if (currentPageNumber > 1) {
            currentPageNumber--
            decorator.setCurrentPage(currentPageNumber)
            onPageChangeListener.run()
        }
    }

    /**
     * Callback for the page number carousel
     *
     * @param newPageNumber Selected page number
     */
    private fun pageChanged(newPageNumber: Int) {
        if (currentPageNumber != newPageNumber) {
            // Don't call setCurrentPage or else it will create a loop with the CarouselDecorator
            currentPageNumber = newPageNumber
            onPageChangeListener.run()
        }
    }

    /**
     * Get the current page number
     *
     * @return Current page number
     */
    fun getCurrentPageNumber(): Int {
        return currentPageNumber
    }

    private fun onCarouselClick() {
        invokeNumberInputDialog(
            pagerPanel.context, R.string.goto_page
        ) { i: Int ->
            if (i in 1..pageCount && i != currentPageNumber) {
                setCurrentPage(i)
                onPageChangeListener.run()
            }
        }
    }

    fun showTooltip(lifecycleOwner: LifecycleOwner) {
        pageCarousel.context.showTooltip(
            R.string.help_page_slider,
            ArrowOrientation.BOTTOM,
            pageCarousel,
            lifecycleOwner
        )
    }
}