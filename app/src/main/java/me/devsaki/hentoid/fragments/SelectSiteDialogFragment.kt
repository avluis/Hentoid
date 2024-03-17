package me.devsaki.hentoid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.DialogSelectSiteBinding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.DrawerItem

/**
 * Dialog to select a site
 */
class SelectSiteDialogFragment : BaseDialogFragment<SelectSiteDialogFragment.Parent>() {

    // UI
    private var binding: DialogSelectSiteBinding? = null

    companion object {
        private const val EXCLUDED_SITES = "EXCLUDED_SITES"
        private const val UNIQUE_ID_ONLY = "UNIQUE_ID_ONLY"
        private const val ALT_SITES = "ALT_SITES"
        private const val TITLE = "TITLE"

        fun invoke(
            activity: FragmentActivity,
            title: String,
            excludedSiteCodes: List<Int> = emptyList(),
            uniqueIdOnly: Boolean = false,
            showAltSites: Boolean = false
        ) {
            val args = getArgs(title, excludedSiteCodes, uniqueIdOnly, showAltSites)
            invoke(activity, SelectSiteDialogFragment(), args)
        }

        fun invoke(
            fragment: Fragment,
            title: String,
            excludedSiteCodes: List<Int> = emptyList(),
            uniqueIdOnly: Boolean = false,
            showAltSites: Boolean = false,
            parentIsActivity: Boolean = false,
        ) {
            val args = getArgs(title, excludedSiteCodes, uniqueIdOnly, showAltSites)
            invoke(fragment, SelectSiteDialogFragment(), args, parentIsActivity = parentIsActivity)
        }

        private fun getArgs(
            title: String,
            excludedSiteCodes: List<Int> = emptyList(),
            uniqueIdOnly: Boolean = false,
            showAltSites: Boolean = false
        ): Bundle {
            val args = Bundle()
            args.putIntegerArrayList(EXCLUDED_SITES, ArrayList(excludedSiteCodes))
            args.putBoolean(UNIQUE_ID_ONLY, uniqueIdOnly)
            args.putBoolean(ALT_SITES, showAltSites)
            args.putString(TITLE, title)
            return args
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View {
        binding = DialogSelectSiteBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        val showUniqueIdsOnly = requireArguments().getBoolean(UNIQUE_ID_ONLY, false)
        val showAltSites = requireArguments().getBoolean(ALT_SITES, false)
        binding?.title?.text = requireArguments().getString(TITLE, "")

        val excludedSites =
            requireArguments().getIntegerArrayList(EXCLUDED_SITES)?.toSet() ?: return
        val sites = Preferences.getActiveSites()
            .filter { !showUniqueIdsOnly || it.hasUniqueBookId() }
            .filterNot { excludedSites.contains(it.code) }
            .sortedBy { it.name }

        val itemAdapter = ItemAdapter<DrawerItem>()
        val items: MutableList<DrawerItem> = ArrayList()
        val userTxt = resources.getString(R.string.user_generic).lowercase()
        sites.forEach {
            items.add(DrawerItem.fromSite(it))
            if (showAltSites && it == Site.PIXIV) {
                val item = DrawerItem(
                    it.description.uppercase() + " ($userTxt)",
                    it.ico,
                    Content.getWebActivityClass(it),
                    it.code.toLong()
                )
                item.site = it
                item.tag = 1 // Specific value
                items.add(item)
            }
        }
        itemAdapter.set(items)

        // Item click listener
        val fastAdapter: FastAdapter<DrawerItem> = FastAdapter.with(itemAdapter)
        fastAdapter.onClickListener = { _, _, i, _ -> onItemSelected(i.site, i.tag as Int?) }
        binding?.recyclerview?.adapter = fastAdapter
    }

    private fun onItemSelected(s: Site?, altCode: Int?): Boolean {
        if (null == s) return false
        parent?.onSiteSelected(s, altCode ?: 0)
        dismiss()
        return true
    }

    interface Parent {
        fun onSiteSelected(site: Site, altCode: Int = 0)
    }
}