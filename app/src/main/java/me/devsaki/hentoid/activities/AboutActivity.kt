package me.devsaki.hentoid.activities

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.mikepenz.aboutlibraries.LibsBuilder
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.URL_DISCORD
import me.devsaki.hentoid.core.URL_GITHUB
import me.devsaki.hentoid.core.URL_GITHUB_WIKI
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.databinding.ActivityAboutBinding
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.fragments.about.ChangelogFragment
import me.devsaki.hentoid.util.ThemeHelper
import me.devsaki.hentoid.util.network.HttpHelper
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AboutActivity : BaseActivity() {

    private var binding: ActivityAboutBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        binding?.apply {
            setContentView(root)

            toolbar.setNavigationOnClickListener { finish() }

            appLogo.setOnClickListener { startBrowserActivity(URL_GITHUB_WIKI) }
            githubText.setOnClickListener { startBrowserActivity(URL_GITHUB) }
            discordText.setOnClickListener { startBrowserActivity(URL_DISCORD) }

            tvVersionName.text = getString(
                R.string.about_app_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )
            tvChromeVersionName.text =
                if (WebkitPackageHelper.getWebViewAvailable())
                    getString(R.string.about_chrome_version, HttpHelper.getChromeVersion())
                else
                    getString(R.string.about_chrome_unavailable)
            tvAndroidApi.text = "Android API ${Build.VERSION.SDK_INT}"

            changelogButton.setOnClickListener { showFragment(ChangelogFragment()) }

            licensesButton.setOnClickListener {
                LibsBuilder()
                    .withLicenseShown(true)
                    .withSearchEnabled(true)
                    .start(this@AboutActivity)
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