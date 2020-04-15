package me.devsaki.hentoid.fragments.intro

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.intro_slide_04.*
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.IntroActivity

// TODO: 6/23/2018 implement ISlidePolicy to force user to select a storage option
class ImportIntroFragment : Fragment(R.layout.intro_slide_04) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val parentActivity = context as IntroActivity
        intro_select_app_folder.setOnClickListener { parentActivity.onCustomStorageSelected() }
    }
}