package me.devsaki.hentoid.views

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.WidgetListPickerBinding


class ListPickerView : ConstraintLayout {
    private val binding = WidgetListPickerBinding.inflate(LayoutInflater.from(context), this, true)

    private var onIndexChangeListener: ((Int) -> Unit)? = null

    var entries: List<String> = emptyList()
        set(value) {
            field = value.toList()
            index = 0
        }

    var index: Int = -1
        set(value) {
            selectIndex(value)
            field = value
        }


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int)
            : super(context, attrs, defStyle) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        val arr = context.obtainStyledAttributes(attrs, R.styleable.ListPickerView)
        try {
            val title = arr.getString(R.styleable.ListPickerView_title) ?: ""
            val rawEntries = arr.getTextArray(R.styleable.ListPickerView_entries)
            if (rawEntries != null) entries = rawEntries.map { cs -> cs.toString() }.toList()

            val currentEntry = arr.getString(R.styleable.ListPickerView_currentEntry) ?: ""
            index = entries.indexOf(currentEntry)

            binding.let {
                it.title.isVisible = title.isNotEmpty()
                if (title.isNotEmpty()) it.title.text = title

                it.description.textSize =
                    (if (title.isEmpty()) resources.getDimension(R.dimen.text_body_1)
                    else resources.getDimension(R.dimen.caption)) / resources.displayMetrics.density
                it.description.text = currentEntry

                it.root.setOnClickListener { onClick() }
            }
        } finally {
            arr.recycle()
        }
    }

    fun setOnIndexChangeListener(listener: (Int) -> Unit) {
        onIndexChangeListener = listener
    }

    private fun onClick() {
        val materialDialog: AlertDialog = MaterialAlertDialogBuilder(context)
            .setSingleChoiceItems(
                entries.toTypedArray(),
                index,
                this::onSelect
            )
            .setCancelable(true)
            .create()

        materialDialog.show()
    }

    private fun onSelect(dialog: DialogInterface, selectedIndex: Int) {
        index = selectedIndex
        onIndexChangeListener?.invoke(selectedIndex)
        dialog.dismiss()
    }

    private fun selectIndex(selectedIndex: Int) {
        if (selectedIndex > -1 && selectedIndex < entries.size)
            binding.description.text = entries[selectedIndex]
    }
}