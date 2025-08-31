package me.devsaki.hentoid.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.customview.widget.ViewDragHelper
import androidx.drawerlayout.widget.DrawerLayout
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.events.CommunicationEvent
import me.devsaki.hentoid.util.Settings
import me.devsaki.hentoid.util.file.removeFile
import me.devsaki.hentoid.util.getFixedContext
import me.devsaki.hentoid.util.network.WebkitPackageHelper
import me.devsaki.hentoid.util.toast
import me.devsaki.hentoid.views.NestedScrollWebView
import me.devsaki.hentoid.workers.UpdateDownloadWorker
import me.devsaki.hentoid.workers.data.UpdateDownloadData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.util.Locale

fun Activity.initDrawerLayout(drawer: DrawerLayout, toolbar: Toolbar) {
    drawer.addDrawerListener(object : ActionBarDrawerToggle(
        this,
        drawer,
        toolbar,
        R.string.open_drawer,
        R.string.close_drawer
    ) {
        /** Called when a drawer has settled in a completely closed state.  */
        override fun onDrawerClosed(view: View) {
            super.onDrawerClosed(view)
            EventBus.getDefault().post(
                CommunicationEvent(
                    CommunicationEvent.Type.CLOSED,
                    CommunicationEvent.Recipient.DRAWER
                )
            )
        }
    })

    // Hack DrawerLayout to make the drag zone larger for the left drawer
    // Source : https://stackoverflow.com/a/36157701/8374722
    try {
        // get dragger responsible for the dragging of the left drawer
        val draggerField = DrawerLayout::class.java.getDeclaredField("mLeftDragger")
        draggerField.isAccessible = true
        val vdh = draggerField[drawer] as ViewDragHelper

        // get access to the private field which defines
        // how far from the edge dragging can start
        val edgeSizeField = ViewDragHelper::class.java.getDeclaredField("mEdgeSize")
        edgeSizeField.isAccessible = true

        // increase the edge size - while x2 should be good enough,
        // try bigger values to easily see the difference
        val origEdgeSizeInt = edgeSizeField.getInt(vdh)
        val newEdgeSize = origEdgeSizeInt * 2
        edgeSizeField.setInt(vdh, newEdgeSize)
        Timber.d("Left drawer : new drag size of %d pixels", newEdgeSize)
    } catch (e: Exception) {
        Timber.e(e)
    }
}