package me.devsaki.hentoid.fragments.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity
import me.devsaki.hentoid.databinding.IntroSlide05Binding
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.Settings.Value.COLOR_THEME_BLACK
import me.devsaki.hentoid.util.Settings.Value.COLOR_THEME_DARK
import me.devsaki.hentoid.util.Settings.Value.COLOR_THEME_LIGHT

class ThemeIntroFragment : Fragment(R.layout.intro_slide_05) {

    private var binding: IntroSlide05Binding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = IntroSlide05Binding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val parentActivity = context as IntroActivity
        binding?.apply {
            themeSelect.check(
                if (COLOR_THEME_DARK == Settings.colorTheme) R.id.intro_5_dark
                else if (COLOR_THEME_BLACK == Settings.colorTheme) R.id.intro_5_black
                else R.id.intro_5_light
            )
            themeSelect.addOnButtonCheckedListener { _, id, b ->
                if (!b) return@addOnButtonCheckedListener

                val theme = when (id) {
                    R.id.intro_5_dark -> COLOR_THEME_DARK
                    R.id.intro_5_black -> COLOR_THEME_BLACK
                    else -> COLOR_THEME_LIGHT
                }
                parentActivity.setThemePrefs(theme)
            }
        }
    }
}
