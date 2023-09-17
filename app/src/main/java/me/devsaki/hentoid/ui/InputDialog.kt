package me.devsaki.hentoid.ui

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.core.view.inputmethod.EditorInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consumer

object InputDialog {

    fun invokeNumberInputDialog(
        context: Context,
        @StringRes message: Int,
        onResult: Consumer<Int>
    ) {
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setRawInputType(Configuration.KEYBOARD_12KEY)
        input.filters =
            arrayOf<InputFilter>(LengthFilter(9)) // We don't expect any number longer than 9 chars (999 million)
        val onOk = DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
            if (input.text.isNotEmpty()) onResult.invoke(input.text.toString().toInt())
            val imm =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(0, 0)
        }
        val onCancel =
            DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, 0)
            }
        showDialog(context, message, input, onOk, onCancel)
    }

    fun invokeInputDialog(
        context: Context,
        @StringRes message: Int,
        onResult: Consumer<String>
    ) {
        invokeInputDialog(context, message, null, onResult, null)
    }

    fun invokeInputDialog(
        context: Context,
        @StringRes message: Int,
        text: String?,
        onResult: Consumer<String>,
        onCancelled: Runnable?
    ) {
        val input = EditText(context)
        if (text != null) input.setText(text)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
        val onOk = DialogInterface.OnClickListener { _, _ ->
            if (input.text.isNotEmpty()) onResult.invoke(
                input.text.toString().trim { it <= ' ' })
            val imm =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(0, 0)
        }
        val onCancel =
            DialogInterface.OnClickListener { _, _ ->
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(0, 0)
                onCancelled?.run()
            }
        showDialog(context, message, input, onOk, onCancel)
    }

    private fun showDialog(
        context: Context,
        @StringRes message: Int,
        input: EditText,
        onOk: DialogInterface.OnClickListener,
        onCancel: DialogInterface.OnClickListener
    ) {
        val materialDialog = MaterialAlertDialogBuilder(context)
            .setView(input)
            .setMessage(message)
            .setPositiveButton(R.string.ok, onOk)
            .setNegativeButton(R.string.cancel, onCancel)
            .create()
        materialDialog.show()
        input.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }
}