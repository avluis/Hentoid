package me.devsaki.hentoid.fragments.reader

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.PrefsActivity
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.databinding.DialogReaderBookPrefsBinding
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Preferences
import org.apache.commons.collections4.map.HashedMap

class ReaderPrefsDialogFragment : BaseDialogFragment<ReaderPrefsDialogFragment.Parent>() {
    companion object {

        const val RENDERING_MODE = "render_mode"
        const val BROWSE_MODE = "browse_mode"
        const val DISPLAY_MODE = "display_mode"

        fun invoke(parent: Fragment, bookPrefs: Map<String, String>) {
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
            invoke(parent, ReaderPrefsDialogFragment(), args)
        }
    }

    // UI
    private var binding: DialogReaderBookPrefsBinding? = null

    // === VARIABLES
    private var renderingMode = 0
    private var browseMode = 0
    private var displayMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        renderingMode = requireArguments().getInt(RENDERING_MODE, -1)
        browseMode = requireArguments().getInt(BROWSE_MODE, -1)
        displayMode = requireArguments().getInt(DISPLAY_MODE, -1)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogReaderBookPrefsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
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

        binding?.apply {
            browsePicker.entries = browseItems
            browsePicker.index = browseMode + 1
        }

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
            renderingItems.add(
                renderingModes[i].replace(
                    " (" + getString(R.string._default) + ")",
                    ""
                )
            )
        }
        binding?.apply {
            renderingPicker.entries = renderingItems
            renderingPicker.index = renderingMode + 1
        }

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
        binding?.apply {
            displayPicker.entries = displayItems
            displayPicker.index = displayMode + 1
        }

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
            val newPrefs: MutableMap<String, String> = HashedMap()
            binding?.apply {
                if (renderingPicker.index > 0) newPrefs[Preferences.Key.VIEWER_RENDERING] =
                    (renderingPicker.index - 1).toString() + ""
                if (browsePicker.index > 0) newPrefs[Preferences.Key.VIEWER_BROWSE_MODE] =
                    (browsePicker.index - 1).toString() + ""
                if (displayPicker.index > 0) newPrefs[Preferences.Key.VIEWER_IMAGE_DISPLAY] =
                    (displayPicker.index - 1).toString() + ""
            }
            parent?.onBookPreferenceChanged(newPrefs)
            dismiss()
        }
    }

    interface Parent {
        fun onBookPreferenceChanged(newPrefs: Map<String, String>)
    }
}