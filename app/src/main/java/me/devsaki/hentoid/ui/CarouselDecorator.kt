package me.devsaki.hentoid.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import me.devsaki.hentoid.core.Consumer
import kotlin.math.max

class CarouselDecorator(context: Context, @LayoutRes private val itemLayout: Int) {
    private val adapter: CarouselAdapter
    private val layoutManager: LinearLayoutManager

    private var pageCount = 0
    private var onPageChangeListener: Consumer<Int>? = null
    private var touchListener: OnTouchListener? = null

    init {
        adapter = CarouselAdapter()
        layoutManager = LinearLayoutManager(context)
        layoutManager.orientation = LinearLayoutManager.HORIZONTAL
    }

    fun setPageCount(pageCount: Int) {
        this.pageCount = pageCount
        adapter.notifyDataSetChanged()
    }

    fun setCurrentPage(page: Int) {
        layoutManager.scrollToPosition(page - 1)
    }

    fun setOnPageChangeListener(onPageChangeListener: Consumer<Int>?) {
        this.onPageChangeListener = onPageChangeListener
    }

    fun setTouchListener(touchListener: OnTouchListener?) {
        this.touchListener = touchListener
    }

    fun decorate(recyclerView: RecyclerView) {
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(CarouselOnScrollListener())
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
    }

    private inner class CarouselOnScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) return
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            val lastCompletelyVisibleItemPosition =
                layoutManager.findLastCompletelyVisibleItemPosition()
            onPageChangeListener?.invoke(
                max(
                    firstVisibleItemPosition,
                    lastCompletelyVisibleItemPosition
                ) + 1
            )
        }
    }

    private inner class CarouselAdapter :
        RecyclerView.Adapter<CarouselViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view: View = inflater.inflate(itemLayout, parent, false)
            view.setOnTouchListener(touchListener)
            return CarouselViewHolder(view)
        }

        override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
            val displayPos = position + 1
            holder.textView.text = "$displayPos"
        }

        override fun getItemCount(): Int {
            return pageCount
        }
    }

    private inner class CarouselViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val textView: TextView

        init {
            textView = itemView as TextView
        }
    }
}