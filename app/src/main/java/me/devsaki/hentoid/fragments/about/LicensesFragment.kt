package me.devsaki.hentoid.fragments.about

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_licenses.*
import me.devsaki.hentoid.R

class LicensesFragment : Fragment(R.layout.fragment_licenses) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        licenses_toolbar.setNavigationOnClickListener { requireActivity().onBackPressed() }
        licenses_web_view.loadUrl("file:///android_asset/licenses.html")
    }
}