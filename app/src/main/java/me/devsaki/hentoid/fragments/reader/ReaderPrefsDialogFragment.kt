package me.devsaki.hentoid.fragments.reader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.PrefsActivity
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.databinding.DialogReaderBookPrefsBinding
import me.devsaki.hentoid.util.Preferences

class ReaderPrefsDialogFragment : DialogFragment() {

    // UI
    private var _binding: DialogReaderBookPrefsBinding? = null
    private val binding get() = _binding!!

    // === VARIABLES
    private var parent: Parent? = null
    private var renderingMode = 0
    private var browseMode = 0
    private var displayMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        renderingMode = requireArguments().getInt(RENDERING_MODE, -1)
        browseMode = requireArguments().getInt(BROWSE_MODE, -1)
        displayMode = requireArguments().getInt(DISPLAY_MODE, -1)
        parent = parentFragment as Parent?
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        _binding = DialogReaderBookPrefsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        parent = null
        _binding = null
        super.onDestroyView()
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val res = rootView.context.resources

        // == Dropdown lists
        val browseModes = resources.getStringArray(R.array.pref_viewer_browse_mode_entries)
        val browseItems: MutableList<String> = ArrayList()
        // App pref
        browseItems.add(
            res.getString(
                R.string.use_app_prefs,
                browseModes[Preferences.getReaderBrowseMode()]
            )
        )
        // Available prefs
        browseItems.addAll(listOf(*browseModes))

        binding.browsePicker.entries = browseItems
        binding.browsePicker.index = browseMode + 1


        val renderingModes = resources.getStringArray(R.array.pref_viewer_rendering_entries)
        val renderingItems: MutableList<String> = ArrayList()
        // App pref
        renderingItems.add(
            res.getString(
                R.string.use_app_prefs,
                renderingModes[if (Preferences.isReaderSmoothRendering()) 1 else 0].replace(
                    " (" + getString(R.string._default) + ")", ""
                )
            )
        )
        // Available prefs
        for (i in renderingModes.indices) {
            // No smooth mode for Android 5
            if (1 == i && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) continue
            renderingItems.add(
                renderingModes[i].replace(
                    " (" + getString(R.string._default) + ")",
                    ""
                )
            )
        }

        binding.renderingPicker.entries = renderingItems
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            binding.renderingPicker.index = 0
            binding.renderingPicker.isEnabled = false
        } else binding.renderingPicker.index = renderingMode + 1


        val displayModes = resources.getStringArray(R.array.pref_viewer_display_mode_entries)
        val displayItems: MutableList<String> = ArrayList()
        // App pref
        displayItems.add(
            res.getString(
                R.string.use_app_prefs,
                displayModes[Preferences.getReaderDisplayMode()]
            )
        )
        // Available prefs
        for (mode in displayModes) {
            displayItems.add(mode.replace(" (" + getString(R.string._default) + ")", ""))
        }

        binding.displayPicker.entries = displayItems
        binding.displayPicker.index = displayMode + 1

        // == Bottom buttons
        val appSettingsBtn = rootView.findViewById<View>(R.id.book_prefs_app_prefs_btn)
        appSettingsBtn.setOnClickListener {
            val intent = Intent(requireActivity(), PrefsActivity::class.java)
            val prefsBundle = PrefsBundle()
            prefsBundle.isViewerPrefs = true
            intent.putExtras(prefsBundle.bundle)
            requireContext().startActivity(intent)
        }

        val okBtn = rootView.findViewById<View>(R.id.action_button)
        okBtn.setOnClickListener {
            val newPrefs: MutableMap<String, String> =
                HashMap()
            if (binding.renderingPicker.index > 0) newPrefs[Preferences.Key.VIEWER_RENDERING] =
                (binding.renderingPicker.index - 1).toString() + ""
            if (binding.browsePicker.index > 0) newPrefs[Preferences.Key.VIEWER_BROWSE_MODE] =
                (binding.browsePicker.index - 1).toString() + ""
            if (binding.displayPicker.index > 0) newPrefs[Preferences.Key.VIEWER_IMAGE_DISPLAY] =
                (binding.displayPicker.index - 1).toString() + ""
            parent?.onBookPreferenceChanged(newPrefs)
            dismiss()
        }
    }

    companion object {

        const val RENDERING_MODE = "render_mode"
        const val BROWSE_MODE = "browse_mode"
        const val DISPLAY_MODE = "display_mode"

        fun invoke(parent: Fragment, bookPrefs: Map<String, String>) {
            val fragment = ReaderPrefsDialogFragment()
            val args = Bundle()
            if (bookPrefs.containsKey(Preferences.Key.VIEWER_RENDERING)) args.putInt(
                RENDERING_MODE,
                if (Preferences.isContentSmoothRendering(bookPrefs)) 1 else 0
            )
            if (bookPrefs.containsKey(Preferences.Key.VIEWER_BROWSE_MODE)) args.putInt(
                BROWSE_MODE,
                Preferences.getContentBrowseMode(bookPrefs)
            )
            if (bookPrefs.containsKey(Preferences.Key.VIEWER_IMAGE_DISPLAY)) args.putInt(
                DISPLAY_MODE,
                Preferences.getContentDisplayMode(bookPrefs)
            )
            fragment.arguments = args
            fragment.show(parent.childFragmentManager, null)
        }
    }

    interface Parent {
        fun onBookPreferenceChanged(newPrefs: Map<String, String>)
    }
}