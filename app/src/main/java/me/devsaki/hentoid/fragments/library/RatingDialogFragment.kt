package me.devsaki.hentoid.fragments.library

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import me.devsaki.hentoid.R
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Debouncer

/**
 * Dialog to assign a rating
 */
class RatingDialogFragment : BaseDialogFragment<RatingDialogFragment.Parent>() {

    companion object {
        private const val RATING = "RATING"
        private const val ITEM_IDS = "ITEM_IDS"

        operator fun invoke(parent: Fragment, itemIds: LongArray, initialRating: Int) {
            val args = Bundle()
            args.putInt(RATING, initialRating)
            args.putLongArray(ITEM_IDS, itemIds)
            invoke(parent, RatingDialogFragment(), args)
        }
    }

    // === UI
    private val stars = mutableListOf<ImageView>()

    // === VARIABLES
    private var initialRating = 0
    private var itemIds: LongArray? = null
    private var closeDebouncer: Debouncer<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialRating = requireArguments().getInt(RATING)
        itemIds = requireArguments().getLongArray(ITEM_IDS)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_library_rating, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        stars.add(rootView.findViewById(R.id.star_1))
        stars.add(rootView.findViewById(R.id.star_2))
        stars.add(rootView.findViewById(R.id.star_3))
        stars.add(rootView.findViewById(R.id.star_4))
        stars.add(rootView.findViewById(R.id.star_5))
        val clearBtn = rootView.findViewById<MaterialButton>(R.id.clear_rating_btn)
        for (i in 0..4) {
            stars[i].setOnClickListener {
                setRating(
                    i + 1,
                    true
                )
            }
        }
        clearBtn.setOnClickListener {
            setRating(
                0,
                true
            )
        }
        setRating(initialRating, false)
        closeDebouncer = Debouncer(
            lifecycleScope, 150
        ) { i: Int ->
            parent?.rateItems(itemIds!!, i)
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroy() {
        closeDebouncer?.clear()
        super.onDestroy()
    }

    override fun onCancel(dialog: DialogInterface) {
        parent?.leaveSelectionMode()
        super.onCancel(dialog)
    }

    private fun setRating(rating: Int, close: Boolean) {
        for (i in 5 downTo 1) stars[i - 1].setImageResource(if (i <= rating) R.drawable.ic_star_full else R.drawable.ic_star_empty)
        if (close) closeDebouncer?.submit(rating)
    }

    interface Parent {
        fun rateItems(itemIds: LongArray, newRating: Int)
        fun leaveSelectionMode()
    }
}