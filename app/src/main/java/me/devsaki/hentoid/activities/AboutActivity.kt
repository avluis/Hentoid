package me.devsaki.hentoid.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.mikepenz.aboutlibraries.LibsBuilder
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.Consts
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.databinding.ActivityAboutBinding
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.fragments.about.ChangelogFragment
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.network.HttpHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AboutActivity : BaseActivity() {

    private var binding: ActivityAboutBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        binding?.let {
            setContentView(it.root)

            it.toolbar.setNavigationOnClickListener { onBackPressed() }

            it.appLogo.setOnClickListener { startBrowserActivity(Consts.URL_GITHUB_WIKI) }
            it.githubText.setOnClickListener { startBrowserActivity(Consts.URL_GITHUB) }
            it.discordText.setOnClickListener { startBrowserActivity(Consts.URL_DISCORD) }
            it.redditText.setOnClickListener { startBrowserActivity(Consts.URL_REDDIT) }

            it.tvVersionName.text = getString(
                R.string.about_app_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
            it.tvChromeVersionName.text =
                getString(R.string.about_chrome_version, HttpHelper.getChromeVersion())

            it.changelogButton.setOnClickListener { showFragment(ChangelogFragment()) }

            it.licensesButton.setOnClickListener {
                LibsBuilder()
                    .withLicenseShown(true)
                    .withSearchEnabled(true)
                    .start(this)
            }
        }

        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            add(android.R.id.content, fragment)
            addToBackStack(null) // This triggers a memory leak in LeakCanary but is _not_ a leak : see https://stackoverflow.com/questions/27913009/memory-leak-in-fragmentmanager
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.hasNewVersion) binding?.changelogButton?.setText(R.string.view_changelog_flagged)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        binding = null
    }
}