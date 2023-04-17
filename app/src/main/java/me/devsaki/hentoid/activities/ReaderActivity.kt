package me.devsaki.hentoid.activities

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.ReaderActivityBundle
import me.devsaki.hentoid.fragments.reader.ReaderGalleryFragment
import me.devsaki.hentoid.fragments.reader.ReaderPagerFragment
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.ToastHelper
import me.devsaki.hentoid.util.file.PermissionHelper
import me.devsaki.hentoid.viewmodels.ReaderViewModel
import me.devsaki.hentoid.viewmodels.ViewModelFactory
import me.devsaki.hentoid.widget.ReaderKeyListener


class ReaderActivity : BaseActivity() {
    private var readerKeyListener: ReaderKeyListener? = null
    private lateinit var viewModel: ReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Preferences.isReaderKeepScreenOn()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val vmFactory = ViewModelFactory(application)
        viewModel = ViewModelProvider(this, vmFactory)[ReaderViewModel::class.java]
        viewModel.observeDbImages(this)

        val intent = intent
        require(!(null == intent || null == intent.extras)) { "Required init arguments not found" }
        val parser = ReaderActivityBundle(intent.extras!!)

        if (parser.isOpenFavPages) {
            // ViewModel hasn't loaded anything yet (fresh start)
            if (null == viewModel.getContent().value) viewModel.loadFavPages()
        } else {
            val contentId = parser.contentId
            require(0L != contentId) { "Incorrect ContentId" }
            val pageNumber = parser.pageNumber
            // ViewModel hasn't loaded anything yet (fresh start)
            if (null == viewModel.getContent().value) {
                val searchParams = parser.searchParams
                if (searchParams != null) viewModel.loadContentFromSearchParams(
                    contentId,
                    pageNumber,
                    searchParams
                ) else viewModel.loadContentFromId(contentId, pageNumber)
            }
        }

        if (!PermissionHelper.requestExternalStorageReadPermission(
                this,
                PermissionHelper.RQST_STORAGE_PERMISSION
            ) &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            ToastHelper.toast(R.string.storage_permission_denied)
            return
        }

        // Allows an full recolor of the status bar with the custom color defined in the activity's theme
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (null == savedInstanceState) {
            val fragment: Fragment =
                if (Preferences.isReaderOpenBookInGalleryMode() || parser.isForceShowGallery) ReaderGalleryFragment() else ReaderPagerFragment()
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commit()
        }
        if (!Preferences.getRecentVisibility()) window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setRunning(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (readerKeyListener != null) readerKeyListener!!.onKey(
            null,
            keyCode,
            event
        ) else super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        if (isFinishing) { // i.e. the activity is closing for good; not being paused / backgrounded
            viewModel.onActivityLeave()
            Preferences.setReaderDeleteAskMode(Preferences.Constant.VIEWER_DELETE_ASK_AGAIN)
            Preferences.setReaderCurrentPageNum(-1)
            Preferences.setReaderCurrentContent(-1)
            setRunning(false)
        }
        super.onStop()
    }

    fun registerKeyListener(listener: ReaderKeyListener?) {
        takeKeyEvents(true)
        readerKeyListener = listener
    }

    fun unregisterKeyListener() {
        readerKeyListener?.clear()
        readerKeyListener = null
    }

    companion object {
        private var isRunning = false

        @Synchronized
        private fun setRunning(value: Boolean) {
            isRunning = value
        }

        @Synchronized
        fun isRunning(): Boolean {
            return isRunning
        }
    }
}