package me.devsaki.hentoid.ui

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.core.view.inputmethod.EditorInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consumer

fun invokeNumberInputDialog(
    context: Context,
    @StringRes message: Int,
    onResult: Consumer<Int>
) {
    val layout = TextInputLayout(context)
    layout.addView(TextInputEditText(context))
    layout.editText?.let { input ->
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setRawInputType(Configuration.KEYBOARD_12KEY)
        input.filters =
            arrayOf<InputFilter>(LengthFilter(9)) // We don't expect any number longer than 9 chars (999 million)
        val onOk = DialogInterface.OnClickListener { _, _ ->
            if (input.text.isNotEmpty()) onResult.invoke(input.text.toString().toInt())
            val imm =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        }
        val onCancel =
            DialogInterface.OnClickListener { _, _ ->
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)
            }
        showDialog(context, message, layout, onOk, onCancel)
    }
}

fun invokeInputDialog(
    context: Context,
    @StringRes message: Int,
    onResult: Consumer<String>,
    text: String = "",
    onCancelled: Runnable? = null
) {
    val layout = TextInputLayout(context)
    layout.addView(TextInputEditText(context))
    layout.endIconMode = END_ICON_CLEAR_TEXT
    layout.editText?.let { input ->
        if (text.isNotEmpty()) input.setText(text)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        val onOk = DialogInterface.OnClickListener { _, _ ->
            if (input.text.isNotEmpty()) onResult.invoke(input.text.toString().trim())
            val imm =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        }
        val onCancel =
            DialogInterface.OnClickListener { _, _ ->
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                onCancelled?.run()
            }
        showDialog(context, message, layout, onOk, onCancel)
    }
}

private fun showDialog(
    context: Context,
    @StringRes message: Int,
    layout: TextInputLayout,
    onOk: DialogInterface.OnClickListener,
    onCancel: DialogInterface.OnClickListener
) {
    val materialDialog = MaterialAlertDialogBuilder(context)
        .setView(layout)
        .setMessage(message)
        .setPositiveButton(R.string.ok, onOk)
        .setNegativeButton(R.string.cancel, onCancel)
        .create()
    materialDialog.show()
    layout.editText?.let {
        it.requestFocus()
        it.postDelayed(
            {
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(it, 0)
            }, 50
        )
    }
}