package me.devsaki.hentoid.fragments.library

import android.content.Context
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
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewholders.TextItem

/**
 * Launcher dialog for the "reach book page by launch code" feature
 */
class SearchContentIdDialogFragment : DialogFragment() {

    companion object {
        private const val ID = "ID"
        private const val FOUND_SITES = "FOUND_SITES"

        operator fun invoke(
            context: Context,
            fragmentManager: FragmentManager,
            id: String,
            siteCodes: List<Int>
        ) {
            val args = Bundle()
            args.putString(ID, id)
            args.putIntegerArrayList(FOUND_SITES, ArrayList(siteCodes))
            val fragment = SearchContentIdDialogFragment()
            ThemeHelper.setStyle(
                context,
                fragment,
                STYLE_NORMAL,
                R.style.Theme_Light_BottomSheetDialog
            )
            fragment.arguments = args
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
        val foundSitesList = requireArguments().getIntegerArrayList(FOUND_SITES)
        val title = ViewCompat.requireViewById<TextView>(rootView, R.id.search_bookid_title)
        title.text = getString(R.string.search_bookid_label, bookId)

        // Not possible for Pururin, e-hentai, exhentai
        val sites: MutableList<Site> = ArrayList()
        if (foundSitesList != null) {
            if (!foundSitesList.contains(Site.HITOMI.code)) sites.add(Site.HITOMI)
            if (!foundSitesList.contains(Site.NHENTAI.code)) sites.add(Site.NHENTAI)
            if (!foundSitesList.contains(Site.ASMHENTAI.code)) sites.add(Site.ASMHENTAI)
            if (!foundSitesList.contains(Site.ASMHENTAI_COMICS.code)) sites.add(Site.ASMHENTAI_COMICS)
            if (!foundSitesList.contains(Site.TSUMINO.code)) sites.add(Site.TSUMINO)
            if (!foundSitesList.contains(Site.LUSCIOUS.code)) sites.add(Site.LUSCIOUS)
            if (!foundSitesList.contains(Site.HBROWSE.code)) sites.add(Site.HBROWSE)
            if (!foundSitesList.contains(Site.HENTAIFOX.code)) sites.add(Site.HENTAIFOX)
            if (!foundSitesList.contains(Site.IMHENTAI.code)) sites.add(Site.IMHENTAI)
            if (!foundSitesList.contains(Site.PIXIV.code)) sites.add(Site.PIXIV)
            if (!foundSitesList.contains(Site.MULTPORN.code)) sites.add(Site.MULTPORN)
            if (!foundSitesList.contains(Site.HDPORNCOMICS.code)) sites.add(Site.HDPORNCOMICS)
        }
        val itemAdapter = ItemAdapter<TextItem<Site>>()
        itemAdapter.set(sites.map { s -> TextItem(s.description, s, true) })

        // Item click listener
        val fastAdapter: FastAdapter<TextItem<Site>> = FastAdapter.with(itemAdapter)
        fastAdapter.onClickListener =
            { _: View?, _: IAdapter<TextItem<Site>>, i: TextItem<Site>, _: Int? ->
                onItemSelected(i.getObject())
            }
        val sitesRecycler =
            ViewCompat.requireViewById<RecyclerView>(rootView, R.id.select_sites)
        sitesRecycler.adapter = fastAdapter
    }

    private fun onItemSelected(s: Site?): Boolean {
        if (null == s) return false
        ContentHelper.launchBrowserFor(
            requireContext(), Content.getGalleryUrlFromId(s, bookId)
        )
        dismiss()
        return true
    }
}