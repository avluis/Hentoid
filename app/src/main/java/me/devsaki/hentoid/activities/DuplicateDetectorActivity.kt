package me.devsaki.hentoid.activities

import android.os.Bundle
import me.devsaki.hentoid.databinding.ActivityDuplicateDetectorBinding
import me.devsaki.hentoid.util.ThemeHelper

class DuplicateDetectorActivity : BaseActivity() {

    private var binding: ActivityDuplicateDetectorBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)

        binding = ActivityDuplicateDetectorBinding.inflate(layoutInflater)
        binding?.let {
            setContentView(it.root)

            it.toolbar.setNavigationOnClickListener { onBackPressed() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }
}