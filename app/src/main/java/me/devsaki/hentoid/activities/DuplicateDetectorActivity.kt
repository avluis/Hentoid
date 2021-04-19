package me.devsaki.hentoid.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.databinding.ActivityDuplicateDetectorBinding
import me.devsaki.hentoid.fragments.tools.DuplicateDetailsFragment
import me.devsaki.hentoid.fragments.tools.DuplicateMainFragment
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.viewmodels.DuplicateViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory

class DuplicateDetectorActivity : BaseActivity() {

    private var binding: ActivityDuplicateDetectorBinding? = null
    private lateinit var viewPager: ViewPager2

    // Viewmodel
    private lateinit var viewModel: DuplicateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)

        binding = ActivityDuplicateDetectorBinding.inflate(layoutInflater)
        binding?.let {
            setContentView(it.root)
            it.toolbar.setNavigationOnClickListener { onBackPressed() }
        }

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory).get(DuplicateViewModel::class.java)

        initUI()
        initToolbar()
        initSelectionToolbar()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun initUI() {
        if (null == binding || null == binding?.duplicatesPager) return

        viewPager = binding?.duplicatesPager as ViewPager2

        // Main tabs
        viewPager.isUserInputEnabled = false // Disable swipe to change tabs

        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                hideSettingsBar()
                updateToolbar()
                updateSelectionToolbar()
            }
        })

        updateDisplay()
    }

    private fun updateDisplay() {
        val pagerAdapter: FragmentStateAdapter = DuplicatePagerAdapter(this)
        viewPager.adapter = pagerAdapter
        pagerAdapter.notifyDataSetChanged()
    }

    fun goBackToMain() {
//        if (isGroupDisplayed()) return
//        viewModel.searchGroup(Preferences.getGroupingDisplay(), query, Preferences.getGroupSortField(), Preferences.isGroupSortDesc(), Preferences.getArtistGroupVisibility(), isGroupFavsChecked)
        viewPager.currentItem = 0
//        if (titles.containsKey(0)) toolbar.setTitle(titles.get(0))
    }

    fun showDetailsFor(content: Content) {
        viewModel.setContent(content)
        viewPager.currentItem = 1
    }

    private fun initToolbar() {
        // TODO
    }

    private fun initSelectionToolbar() {
        // TODO
    }

    private fun hideSettingsBar() {
        // TODO
    }

    private fun updateToolbar() {
        // TODO
    }

    private fun updateSelectionToolbar() {
        // TODO
    }


    /**
     * ============================== SUBCLASS
     */
    private class DuplicatePagerAdapter constructor(fa: FragmentActivity?) : FragmentStateAdapter(fa!!) {
        override fun createFragment(position: Int): Fragment {
            return if (0 == position) {
                DuplicateMainFragment()
            } else {
                DuplicateDetailsFragment()
            }
        }

        override fun getItemCount(): Int {
            return 2
        }
    }
}