package me.devsaki.hentoid.util

import android.content.Context
import me.devsaki.hentoid.database.ObjectBoxDB
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.AchievementEvent
import org.greenrobot.eventbus.EventBus
import kotlin.math.pow

object AchievementsManager {

    private val syncObject = Any()
    private var storageCache = Settings.achievements

    fun checkPrefs(context: Context) {
        if (!isRegistered(1)) {
            if (1 == Preferences.getActiveSites().size) registerAndSignal(1)
        }
    }

    fun checkCollection(context: Context) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            val eligibleContent = db.selectEligibleContentIds();
            if (!isRegistered(3) || !isRegistered(4) || !isRegistered(5)) {
                val readPages = db.selectTotalReadPages()
                if (readPages >= 5000) registerAndSignal(5)
                if (readPages >= 1000) registerAndSignal(4)
                if (readPages >= 500) registerAndSignal(3)
            }
            if (!isRegistered(6) || !isRegistered(7) || !isRegistered(8)) {
                val largestArtist = db.selectLargestArtist(eligibleContent, 50)
                if (largestArtist >= 50) registerAndSignal(8)
                if (largestArtist >= 30) registerAndSignal(7)
                if (largestArtist >= 10) registerAndSignal(6)
            }
            if (!isRegistered(9) || !isRegistered(10) || !isRegistered(11)) {
                if (eligibleContent.size >= 1000) registerAndSignal(9)
                if (eligibleContent.size >= 2000) registerAndSignal(10)
                if (eligibleContent.size >= 5000) registerAndSignal(11)
            }
            if (!isRegistered(12)) {
                val count = db.countWithTagsOr(eligibleContent, "netorare", "ntr")
                if (count >= 20) registerAndSignal(12)
            }
            if (!isRegistered(13)) {
                val count = db.countWithTagsAnd(eligibleContent, "glasses", "big ass")
                if (count >= 20) registerAndSignal(13)
            }
            if (!isRegistered(14)) {
                val count = db.countWithTagsOr(eligibleContent, "lingerie")
                if (count >= 20) registerAndSignal(14)
            }
            if (!isRegistered(15)) {
                val count = db.countWithTagsOr(eligibleContent, "stockings")
                if (count >= 20) registerAndSignal(15)
            }
            if (!isRegistered(19)) {
                val invisibleSites = Site.entries.filter { e -> !e.isVisible }
                val count = db.countWithSitesOr(eligibleContent, invisibleSites)
                if (count >= 10) registerAndSignal(19)
            }
        } finally {
            db.cleanup()
        }
    }

    fun trigger(id: Int) {
        //if (isRegistered(id)) return
        registerAndSignal(id)
    }

    private fun registerAndSignal(id: Int) {
        register(id)
        EventBus.getDefault().postSticky(AchievementEvent(id))
    }

    private fun register(id: Int) {
        if (id > 63) throw Exception("The app doesn't support more than 64 different achievements")
        val storageId = 2f.pow(id).toULong()
        synchronized(syncObject) {
            storageCache = storageCache or storageId
            Settings.achievements = storageCache
        }
    }

    private fun isRegistered(id: Int): Boolean {
        return isRegistered(id, storageCache)
    }

    private fun isRegistered(id: Int, storage: ULong): Boolean {
        if (id > 63) throw Exception("The app doesn't support more than 64 different achievements")
        val storageId = 2f.pow(id).toULong()
        return (storage and storageId > 0UL)
    }
}