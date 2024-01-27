package me.devsaki.hentoid.fragments.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.viewholders.DrawerItem

/**
 * Launcher dialog for the "reach book page by launch code" feature
 */
class SearchContentIdDialogFragment : DialogFragment() {

    companion object {
        private const val ID = "ID"
        private const val FOUND_SITES = "FOUND_SITES"

        operator fun invoke(
            fragmentManager: FragmentManager,
            id: String,
            siteCodes: List<Int>
        ) {
            val args = Bundle()
            args.putString(ID, id)
            args.putIntegerArrayList(FOUND_SITES, ArrayList(siteCodes))
            val fragment = SearchContentIdDialogFragment()
            fragment.arguments = args
            fragment.isCancelable = true
            fragment.show(fragmentManager, null)
        }
    }

    private lateinit var bookId: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_library_search_id, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        bookId = requireArguments().getString(ID, "")
        val title: TextView = rootView.requireById(R.id.search_bookid_title)
        title.text = getString(R.string.search_bookid_label, bookId)

        val foundSites = requireArguments().getIntegerArrayList(FOUND_SITES) ?: return
        val sites = Preferences.getActiveSites()
            .filter { it.hasUniqueBookId() }
            .filterNot { foundSites.contains(it.code) }
            .sortedBy { it.name }

        val itemAdapter = ItemAdapter<DrawerItem>()
        val items: MutableList<DrawerItem> = ArrayList()
        val userTxt = resources.getString(R.string.user_generic).lowercase()
        sites.forEach {
            items.add(DrawerItem.fromSite(it))
            if (it == Site.PIXIV) {
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
        val sitesRecycler =
            ViewCompat.requireViewById<RecyclerView>(rootView, R.id.select_sites)
        sitesRecycler.adapter = fastAdapter
    }

    private fun onItemSelected(s: Site?, altCode: Int?): Boolean {
        if (null == s) return false
        ContentHelper.launchBrowserFor(
            requireContext(), Content.getGalleryUrlFromId(s, bookId, altCode ?: 0)
        )
        dismiss()
        return true
    }
}