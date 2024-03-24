package me.devsaki.hentoid.fragments.intro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity
import me.devsaki.hentoid.databinding.IntroSlide05Binding
import me.devsaki.hentoid.util.Preferences

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
            intro5Light.setOnClickListener { parentActivity.setThemePrefs(Preferences.Constant.COLOR_THEME_LIGHT) }
            intro5Dark.setOnClickListener { parentActivity.setThemePrefs(Preferences.Constant.COLOR_THEME_DARK) }
            intro5Black.setOnClickListener { parentActivity.setThemePrefs(Preferences.Constant.COLOR_THEME_BLACK) }
        }
    }
}
