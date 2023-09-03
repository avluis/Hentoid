package me.devsaki.hentoid.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import me.devsaki.hentoid.R

class MissingWebViewActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_missing_web_view)
        findViewById<View>(R.id.open_library).setOnClickListener {
            onOpenLibraryPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun onOpenLibraryPressed() {
        // from me.devsaki.hentoid.activities.sources.BaseWebActivity.goHome()
        val intent = Intent(this, LibraryActivity::class.java)
        // If FLAG_ACTIVITY_CLEAR_TOP is not set,
        // it can interfere with Double-Back (press back twice) to exit
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
        finish()
    }
}