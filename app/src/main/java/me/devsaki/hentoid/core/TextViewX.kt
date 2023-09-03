package me.devsaki.hentoid.core

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import me.devsaki.hentoid.util.Debouncer

fun TextView.setOnTextChangedListener(
    scope: LifecycleCoroutineScope,
    listener: (value: String) -> Unit
) {
    addTextChangedListener(
        object : TextWatcher {
            private val debouncer: Debouncer<String> = Debouncer(scope, 750) { s: String ->
                listener.invoke(s)
            }

            override fun afterTextChanged(s: Editable?) {
                if (s != null) debouncer.submit(s.toString())
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
