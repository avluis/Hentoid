package me.devsaki.hentoid.fragments.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.IntroSlide06Binding
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.viewholders.SiteItem

class SourcesIntroFragment : Fragment(R.layout.intro_slide_06) {
    private var binding: IntroSlide06Binding? = null

    private val itemAdapter = ItemAdapter<SiteItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = IntroSlide06Binding.inflate(inflater, container, false)

        // Recycler
        val items: MutableList<SiteItem> = ArrayList()
        for (s in Site.entries.sortedBy { s -> s.description })
            if (s.isVisible) items.add(
                SiteItem(s, selected = true, showHandle = false)
            )
        itemAdapter.set(items)

        val fastAdapter: FastAdapter<SiteItem> = FastAdapter.with(itemAdapter)
        binding?.list?.adapter = fastAdapter

        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    fun getSelection(): List<Site> {
        val result: MutableList<Site> = ArrayList()
        for (s in itemAdapter.adapterItems) if (s.isSelected) result.add(s.site)
        return result
    }
}