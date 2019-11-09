package me.devsaki.hentoid.fragments.intro

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.intro_slide_05.*
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity
import me.devsaki.hentoid.util.Preferences

class ThemeIntroFragment : Fragment(R.layout.intro_slide_05) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val parentActivity = context as IntroActivity
        intro_5_light.setOnClickListener { parentActivity.setThemePrefs(Preferences.Constant.DARK_MODE_OFF) }
        intro_5_dark.setOnClickListener { parentActivity.setThemePrefs(Preferences.Constant.DARK_MODE_ON) }
    }
}
