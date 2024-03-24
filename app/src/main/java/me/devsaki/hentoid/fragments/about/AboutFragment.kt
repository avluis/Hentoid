package me.devsaki.hentoid.fragments.about

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.AboutActivity
import me.devsaki.hentoid.core.URL_DISCORD
import me.devsaki.hentoid.core.URL_GITHUB
import me.devsaki.hentoid.core.URL_GITHUB_WIKI
import me.devsaki.hentoid.core.startBrowserActivity
import me.devsaki.hentoid.databinding.FragmentAboutBinding
import me.devsaki.hentoid.events.UpdateEvent
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.network.getChromeVersion
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.ref.WeakReference

// TODO - invisible init while loading
class AboutFragment : Fragment(R.layout.fragment_about) {

    private var binding: FragmentAboutBinding? = null

    private lateinit var activity: WeakReference<AboutActivity>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(requireActivity() is AboutActivity) { "Parent activity has to be a LibraryActivity" }
        activity = WeakReference(requireActivity() as AboutActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.apply {
            appLogo.setOnClickListener { requireContext().startBrowserActivity(URL_GITHUB_WIKI) }
            githubText.setOnClickListener { requireContext().startBrowserActivity(URL_GITHUB) }
            discordText.setOnClickListener { requireContext().startBrowserActivity(URL_DISCORD) }

            tvVersionName.text = getString(
                R.string.about_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
            )
            tvChromeVersionName.text = if (WebkitPackageHelper.getWebViewAvailable()) getString(
                R.string.about_chrome_version, getChromeVersion()
            )
            else getString(R.string.about_chrome_unavailable)
            tvAndroidApi.text = getString(
                R.string.about_api, Build.VERSION.SDK_INT
            )

            changelogButton.setOnClickListener { activity.get()?.showChangelog() }
            licensesButton.setOnClickListener { activity.get()?.showLicences() }
            achievementsButton.setOnClickListener { activity.get()?.showAchievements() }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onUpdateEvent(event: UpdateEvent) {
        if (event.hasNewVersion) binding?.changelogButton?.setText(R.string.view_changelog_flagged)
    }
}