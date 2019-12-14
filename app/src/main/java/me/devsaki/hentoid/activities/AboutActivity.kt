package me.devsaki.hentoid.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import kotlinx.android.synthetic.main.activity_about.*
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.fragments.about.ChangelogFragment
import me.devsaki.hentoid.fragments.about.LicensesFragment
import me.devsaki.hentoid.util.Consts
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.startBrowserActivity
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AboutActivity : AppCompatActivity(R.layout.activity_about) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        toolbar.setNavigationOnClickListener { onBackPressed() }

        appLogo.setOnClickListener { startBrowserActivity(Consts.URL_GITHUB_WIKI) }
        githubText.setOnClickListener { startBrowserActivity(Consts.URL_GITHUB) }
        discordText.setOnClickListener { startBrowserActivity(Consts.URL_DISCORD) }
        redditText.setOnClickListener { startBrowserActivity(Consts.URL_REDDIT) }

        tv_version_name.text = getString(R.string.about_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        tv_chrome_version_name.text = getString(R.string.about_chrome_version, Helper.getChromeVersion(this))

        changelogButton.setOnClickListener { showFragment(ChangelogFragment()) }

        licensesButton.setOnClickListener { showFragment(LicensesFragment()) }

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
        if (event.hasNewVersion) changelogButton.setText(R.string.view_changelog_flagged)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
    }
}