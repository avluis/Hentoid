package me.devsaki.hentoid.core

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView

fun TextView.setOnTextChangedListener(listener: (value: String) -> Unit) {
    addTextChangedListener(
        object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null) listener.invoke(s.toString())
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                // Nothing to override here
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                // Nothing to override here
            }
        }
    )
}
