package me.devsaki.hentoid.fragments.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.setOnTextChangedListener
import me.devsaki.hentoid.databinding.DialogPrefsImportNamePatternBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import timber.log.Timber
import java.util.regex.Pattern


class ImportNamePatternDialogFragment : BaseDialogFragment<Nothing>() {

    companion object {
        fun invoke(parentFragment: Fragment) {
            invoke(parentFragment, ImportNamePatternDialogFragment())
        }
    }

    private var binding: DialogPrefsImportNamePatternBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogPrefsImportNamePatternBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        binding?.apply {
            // Select sample values
            selector.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                when (checkedId) {
                    R.id.choice_a -> {
                        pattern.editText?.setText("[%a] %t")
                        fileName.editText?.setText(getString(R.string.import_ext_name_pattern_a))
                    }

                    R.id.choice_b -> {
                        pattern.editText?.setText("%a - %t")
                        fileName.editText?.setText(getString(R.string.import_ext_name_pattern_b))
                    }

                    R.id.choice_c -> {
                        pattern.editText?.setText("%t (%a)")
                        fileName.editText?.setText(getString(R.string.import_ext_name_pattern_c))
                    }
                }
            }
            // Disable sample selectors when editing text
            pattern.editText?.setText(Settings.importExtNamePattern)
            pattern.editText?.setOnTextChangedListener(lifecycleScope)
            {
                selector.clearChecked()
                testFile(
                    fileName.editText?.text?.toString() ?: "",
                    it
                )
            }
            // Make test panel fold/unfold
            testPanel.setOnClickListener {
                if (testGrp.isVisible) {
                    dropBarIcon.setImageResource(R.drawable.ic_drop_down)
                    testGrp.isVisible = false
                } else {
                    dropBarIcon.setImageResource(R.drawable.ic_drop_up)
                    testGrp.isVisible = true
                }
            }
            // Test whenever test file name is changed
            fileName.editText?.setOnTextChangedListener(lifecycleScope) {
                testFile(
                    it,
                    pattern.editText?.text?.toString() ?: ""
                )
            }
            // Save and dismiss
            actionButton.setOnClickListener { onActionClick() }
        }
    }

    private fun testFile(input: String, patternInput: String) {
        Timber.d("TESTING $input $patternInput")
        var pattern = if (patternInput.isBlank()) "%t" else patternInput

        // Visual warnings
        var hasTitle = true
        var hasArtist = true
        if (!pattern.contains("%t")) {
            binding?.apply {
                titleVal.text = getString(R.string.import_ext_test_title_warning)
            }
            hasTitle = false
        }
        if (!pattern.contains("%a")) {
            binding?.apply {
                artistVal.text = getString(R.string.import_ext_test_artist_warning)
            }
            hasArtist = false
        }

        // Turn into regexp
        // Unescape special chars
        var regexp =
            pattern.replace("\\", "\\\\")
                .replace("[", "\\[").replace("]", "\\]")
                .replace("(", "\\(").replace(")", "\\)")
                .replace("{", "\\{").replace("}", "\\}")
                .replace("?", "\\?").replace("*", "\\*").replace("+", "\\+").replace(".", "\\.")

        // Turn patterns into capturing groups
        if (hasTitle) regexp = regexp.replace("%t", "(?<title>[\\w\\-_%]+)")
        if (hasArtist) regexp = regexp.replace("%a", "(?<artist>[\\w\\-_%]+)")
        Timber.d("RGX $regexp")
        // TODO Turn free patterns into non-capturing groups
        // Compile regex with unicode support
        val rgx = regexp.toPattern(Pattern.UNICODE_CASE)

        // Test input against newly compiled pattern
        var theTitle = getString(R.string.import_ext_test_title_not_found)
        var theArtist = getString(R.string.import_ext_test_artist_not_found)
        val matcher = rgx.matcher(input)
        if (matcher.matches()) {
            if (hasTitle) matcher.group("title")?.let { theTitle = it }
            if (hasArtist) matcher.group("artist")?.let { theArtist = it }
        }
        binding?.apply {
            if (hasTitle) titleVal.text = theTitle
            if (hasArtist) artistVal.text = theArtist
        }
    }

    private fun onActionClick() {
        // TODO signal if no %t set ?
        binding?.apply {
            Settings.importExtNamePattern = pattern.editText?.text?.toString() ?: ""
        }
        dismissAllowingStateLoss()
    }
}