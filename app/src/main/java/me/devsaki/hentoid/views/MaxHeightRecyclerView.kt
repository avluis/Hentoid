package me.devsaki.hentoid.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import androidx.recyclerview.widget.RecyclerView
import me.devsaki.hentoid.R

class MaxHeightRecyclerView : RecyclerView {
    private var mMaxHeight = 0

    constructor (context: Context) : super(context)

    constructor (context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    constructor (context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        context.withStyledAttributes(attrs, R.styleable.MaxHeightRecyclerView) {
            mMaxHeight = getLayoutDimension(R.styleable.MaxHeightRecyclerView_maxHeight, mMaxHeight)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var hms = heightMeasureSpec
        if (mMaxHeight > 0) {
            hms = MeasureSpec.makeMeasureSpec(mMaxHeight, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, hms)
    }
}