package me.devsaki.hentoid.activities

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.aboutlibraries.LibsBuilder
import com.mikepenz.aboutlibraries.LibsConfiguration
import me.devsaki.hentoid.R
import me.devsaki.hentoid.databinding.ActivityAboutBinding
import me.devsaki.hentoid.fragments.about.AboutFragment
import me.devsaki.hentoid.fragments.about.AchievementsFragment
import me.devsaki.hentoid.fragments.about.ChangelogFragment
import me.devsaki.hentoid.util.AchievementsManager
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.widget.ScrollPositionListener


class AboutActivity : BaseActivity() {

    private var binding: ActivityAboutBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)

            toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

            showFragment(AboutFragment(), false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    fun showLicences() {
        val libsUIListener: LibsConfiguration.LibsUIListener =
            object : LibsConfiguration.LibsUIListener {
                override fun preOnCreateView(view: View): View {
                    // Nothing to do here
                    return view
                }

                override fun postOnCreateView(view: View): View {
                    val recyclerView =
                        view.findViewById<RecyclerView>(com.mikepenz.aboutlibraries.R.id.cardListView)
                    recyclerView.setBackgroundColor(
                        ThemeHelper.getColor(
                            this@AboutActivity,
                            R.color.window_background_light
                        )
                    )
                    val scrollListener = ScrollPositionListener { _ -> /* Nothing */ }
                    scrollListener.setOnEndOutOfBoundScrollListener {
                        AchievementsManager.trigger(16)
                    }
                    recyclerView.addOnScrollListener(scrollListener)
                    return view
                }
            }

        showFragment(
            LibsBuilder().withLicenseShown(true).withSearchEnabled(true)
                .withUiListener(libsUIListener).supportFragment()
        )
    }

    fun showChangelog() {
        showFragment(ChangelogFragment())
    }

    fun showAchievements() {
        showFragment(AchievementsFragment())
    }

    private fun showFragment(fragment: Fragment, keepHistory: Boolean = true) {
        supportFragmentManager.commit {
            add(R.id.frame_container, fragment)
            if (keepHistory) addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }
}