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
    private var onValueChangeListener: ((String) -> Unit)? = null
    private var values: List<String> = emptyList()

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

    var value: String
        set(value) {
            index = values.indexOf(value)
        }
        get() {
            return if (index > -1 && index < values.size) values[index]
            else ""
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
            if (rawEntries != null) entries = rawEntries.map { cs -> cs.toString() }
            val rawValues = arr.getTextArray(R.styleable.ListPickerView_values)
            if (rawValues != null) values = rawValues.map { cs -> cs.toString() }

            binding.let {
                it.root.clipToOutline = true
                it.title.isVisible = title.isNotEmpty()
                if (title.isNotEmpty()) it.title.text = title

                it.description.textSize =
                    (if (title.isEmpty()) resources.getDimension(R.dimen.text_body_1)
                    else resources.getDimension(R.dimen.caption)) / resources.displayMetrics.density
                it.description.text = ""

                it.root.setOnClickListener { onClick() }
            }
        } finally {
            arr.recycle()
        }
    }

    fun setOnIndexChangeListener(listener: (Int) -> Unit) {
        onIndexChangeListener = listener
    }

    fun setOnValueChangeListener(listener: (String) -> Unit) {
        onValueChangeListener = listener
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
        if (value.isNotEmpty()) onValueChangeListener?.invoke(value)
        dialog.dismiss()
    }

    private fun selectIndex(selectedIndex: Int) {
        if (selectedIndex > -1 && selectedIndex < entries.size)
            binding.description.text = entries[selectedIndex]
    }
}