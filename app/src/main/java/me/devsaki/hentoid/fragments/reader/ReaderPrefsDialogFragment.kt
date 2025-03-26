package me.devsaki.hentoid.fragments.reader

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.PrefsBundle
import me.devsaki.hentoid.activities.prefs.PreferencesActivity
import me.devsaki.hentoid.databinding.DialogReaderBookPrefsBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.fragments.BaseDialogFragment
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Key.VIEWER_BROWSE_MODE
import me.devsaki.hentoid.util.Settings.Key.VIEWER_IMAGE_DISPLAY
import me.devsaki.hentoid.util.Settings.Key.VIEWER_RENDERING
import me.devsaki.hentoid.util.Settings.Value.VIEWER_BROWSE_TTB
import kotlin.math.min

class ReaderPrefsDialogFragment : BaseDialogFragment<ReaderPrefsDialogFragment.Parent>() {
    companion object {

        const val RENDERING_MODE = "render_mode"
        const val BROWSE_MODE = "browse_mode"
        const val TWOPAGES_MODE = "twopages_mode"
        const val DISPLAY_MODE = "display_mode"
        const val SITE = "site"

        fun invoke(parent: Fragment, site: Site, bookPrefs: Map<String, String>) {
            val args = Bundle()
            if (bookPrefs.containsKey(VIEWER_RENDERING)) args.putInt(
                RENDERING_MODE,
                if (Settings.isContentSmoothRendering(bookPrefs)) 1 else 0
            )
            if (bookPrefs.containsKey(VIEWER_BROWSE_MODE)) args.putInt(
                BROWSE_MODE,
                Settings.getContentBrowseMode(site, bookPrefs)
            )
            if (bookPrefs.containsKey(Settings.Key.READER_TWOPAGES)) args.putBoolean(
                TWOPAGES_MODE,
                Settings.getContent2PagesMode(bookPrefs)
            )
            if (bookPrefs.containsKey(VIEWER_IMAGE_DISPLAY)) args.putInt(
                DISPLAY_MODE,
                Settings.getContentDisplayMode(site, bookPrefs)
            )
            args.putInt(SITE, site.code)
            invoke(parent, ReaderPrefsDialogFragment(), args)
        }
    }

    // UI
    private var binding: DialogReaderBookPrefsBinding? = null

    // === VARIABLES
    private var bookRenderingMode = 0
    private var bookBrowseMode = 0
    private var bookDisplayMode = 0
    private var bookTwoPagesMode = false
    private var hasSiteBrowseMode = false
    private var siteBrowseMode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireNotNull(arguments) { "No arguments found" }
        bookRenderingMode = requireArguments().getInt(RENDERING_MODE, -1)
        bookBrowseMode = requireArguments().getInt(BROWSE_MODE, -1)
        bookDisplayMode = requireArguments().getInt(DISPLAY_MODE, -1)
        bookTwoPagesMode = requireArguments().getBoolean(TWOPAGES_MODE, false)
        val site = Site.searchByCode(requireArguments().getInt(SITE, Site.ANY.code).toLong())
        siteBrowseMode = Settings.getReaderBrowseMode(site)
        hasSiteBrowseMode = siteBrowseMode != Settings.appReaderBrowseMode
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        binding = DialogReaderBookPrefsBinding.inflate(inflater, container, false)
        return binding?.root
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
        val browseItems = ArrayList<String>()

        // App pref
        browseItems.add(
            res.getString(R.string.use_app_prefs, browseModes[Settings.appReaderBrowseMode])
        )
        // Site pref, if different
        if (hasSiteBrowseMode)
            browseItems.add(res.getString(R.string.use_source_prefs, browseModes[siteBrowseMode]))
        // Available prefs
        browseItems.addAll(listOf(*browseModes))

        binding?.apply {
            browsePicker.entries = browseItems
            browsePicker.index = min(bookBrowseMode + 1, browsePicker.entries.size - 1)
            browsePicker.setOnIndexChangeListener { refreshValues() }
            twoPagesSwitch.isChecked = bookTwoPagesMode
        }

        val renderingModes = resources.getStringArray(R.array.pref_viewer_rendering_entries)
        val renderingItems: MutableList<String> = ArrayList()
        // App pref
        renderingItems.add(
            res.getString(
                R.string.use_app_prefs,
                renderingModes[if (Settings.isReaderSmoothRendering()) 1 else 0].replace(
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
            renderingPicker.index = bookRenderingMode + 1
        }

        val displayModes = resources.getStringArray(R.array.pref_viewer_display_mode_entries)
        val displayItems: MutableList<String> = ArrayList()
        // App pref
        displayItems.add(
            res.getString(
                R.string.use_app_prefs,
                displayModes[Settings.readerDisplayMode]
            )
        )
        // Available prefs
        for (mode in displayModes) {
            displayItems.add(mode.replace(" (" + getString(R.string._default) + ")", ""))
        }
        binding?.apply {
            displayPicker.entries = displayItems
            displayPicker.index = bookDisplayMode + 1
        }

        // == Bottom buttons
        binding?.appPrefsBtn?.setOnClickListener {
            val intent = Intent(requireActivity(), PreferencesActivity::class.java)
            val prefsBundle = PrefsBundle()
            prefsBundle.isViewerPrefs = true
            intent.putExtras(prefsBundle.bundle)
            requireContext().startActivity(intent)
        }

        binding?.actionButton?.setOnClickListener {
            val newPrefs: MutableMap<String, String> = HashMap()
            binding?.apply {
                if (renderingPicker.index > 0) newPrefs[VIEWER_RENDERING] =
                    (renderingPicker.index - 1).toString()
                if (browsePicker.index > 0) newPrefs[VIEWER_BROWSE_MODE] =
                    (browsePicker.index - 1).toString()
                newPrefs[Settings.Key.READER_TWOPAGES] = twoPagesSwitch.isChecked.toString()
                if (displayPicker.index > 0) newPrefs[VIEWER_IMAGE_DISPLAY] =
                    (displayPicker.index - 1).toString()
            }
            parent?.onBookPreferenceChanged(newPrefs)
            dismiss()
        }
    }

    private fun refreshValues() {
        binding?.apply {
            twoPagesSwitch.isVisible = (VIEWER_BROWSE_TTB != browsePicker.index - 1)
            if (0 == browsePicker.index && Settings.appReaderBrowseMode == VIEWER_BROWSE_TTB)
                twoPagesSwitch.isVisible = false
            if (!twoPagesSwitch.isVisible) twoPagesSwitch.isChecked = false
        }
    }

    interface Parent {
        fun onBookPreferenceChanged(newPrefs: Map<String, String>)
    }
}