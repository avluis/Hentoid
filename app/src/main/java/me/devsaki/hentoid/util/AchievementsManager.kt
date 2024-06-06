package me.devsaki.hentoid.util

import android.content.Context
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp
import me.devsaki.hentoid.database.AchievementsDAO
import me.devsaki.hentoid.database.domains.Achievement
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.events.AchievementEvent
import me.devsaki.hentoid.json.core.JsonAchievements
import me.devsaki.hentoid.util.file.isLowDeviceStorage
import me.devsaki.hentoid.util.file.readStreamAsString
import org.apache.commons.collections4.map.HashedMap
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.time.Instant
import kotlin.math.max
import kotlin.math.pow

object AchievementsManager {

    private val syncObject = Any()

    private var storageCache = Settings.achievements

    val masterdata: Map<Int, Achievement> by lazy { init(HentoidApp.getInstance()) }

    fun init(context: Context): Map<Int, Achievement> {
        val result = HashedMap<Int, Achievement>()

        context.resources.openRawResource(R.raw.achievements).use { `is` ->
            val achievementsStr = readStreamAsString(`is`)
            val achievementsObj = JsonHelper.jsonToObject(
                achievementsStr,
                JsonAchievements::class.java
            )
            achievementsObj.achievements.forEach { entry ->
                val id = entry.id
                val title = context.resources.getIdentifier(
                    "ach_name_$id",
                    "string",
                    context.packageName
                )
                val desc = context.resources.getIdentifier(
                    "ach_desc_$id",
                    "string",
                    context.packageName
                )
                result[entry.id] = Achievement(
                    entry.id,
                    entry.type,
                    false,
                    title,
                    desc,
                    R.drawable.ic_achievement
                )
            }
        }
        result[62] = Achievement(
            62,
            Achievement.Type.GOLD,
            true,
            R.string.ach_name_62,
            R.string.ach_desc_62,
            R.drawable.ic_star_full
        )
        result[63] = Achievement(
            63,
            Achievement.Type.GOLD,
            true,
            R.string.ach_name_63,
            R.string.ach_desc_63,
            R.drawable.ic_star_full
        )
        return result
    }

    fun checkPrefs() {
        if (!isRegistered(1)) {
            if (1 == Preferences.getActiveSites().size) registerAndSignal(1)
        }
    }

    fun checkStorage(context: Context) {
        if (!isRegistered(18)) {
            if (context.isLowDeviceStorage(98)) registerAndSignal(18)
        }
    }

    fun checkCollection() {
        val db = AchievementsDAO()
        val now = Instant.now().toEpochMilli()
        try {
            val eligibleContent = db.selectEligibleContentIds();
            if (!isRegistered(3) || !isRegistered(4) || !isRegistered(5)) {
                val readPages = db.selectTotalReadPages()
                if (!isRegistered(5) && readPages >= 5000) registerAndSignal(5)
                if (!isRegistered(4) && readPages >= 1000) registerAndSignal(4)
                if (!isRegistered(3) && readPages >= 500) registerAndSignal(3)
            }
            if (!isRegistered(6) || !isRegistered(7) || !isRegistered(8)) {
                val largestArtist = db.selectLargestArtist(eligibleContent, 50)
                if (!isRegistered(8) && largestArtist >= 50) registerAndSignal(8)
                if (!isRegistered(7) && largestArtist >= 30) registerAndSignal(7)
                if (!isRegistered(6) && largestArtist >= 10) registerAndSignal(6)
            }
            if (!isRegistered(9) && eligibleContent.size >= 1000) registerAndSignal(9)
            if (!isRegistered(10) && eligibleContent.size >= 2000) registerAndSignal(10)
            if (!isRegistered(11) && eligibleContent.size >= 5000) registerAndSignal(11)
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
            if (!isRegistered(17) && eligibleContent.isNotEmpty()) {
                val newestRead = db.selectNewestRead()
                val newestDownload = db.selectNewestDownload()
                val maxDiff = now - max(newestRead, newestDownload)
                if (maxDiff >= 72 * 60 * 60 * 1000) registerAndSignal(17)
            }
            if (!isRegistered(19)) {
                val invisibleSites =
                    Site.entries.filter { e -> !e.isVisible }.filterNot { e -> e == Site.NONE }
                val count = db.countWithSitesOr(eligibleContent, invisibleSites)
                if (count >= 10) registerAndSignal(19)
            }
            if (!isRegistered(21)) {
                val count = db.countWithTagsOr(eligibleContent, "x-ray", "nakadashi")
                if (count >= 20) registerAndSignal(21)
            }
            if (!isRegistered(22)) {
                val count = db.countWithTagsOr(eligibleContent, "story arc")
                if (count >= 20) registerAndSignal(22)
            }
            if (!isRegistered(23)) {
                if (db.hasAtLeastCHapters(eligibleContent, 300)) registerAndSignal(23)
            }
            if (!isRegistered(24)) {
                val nbBooks = eligibleContent.size
                val nbUngroupedBooks = db.selectUngroupedContentIds().size
                if (nbBooks >= 100 && 0 == nbUngroupedBooks) registerAndSignal(24)
            }
            if (!isRegistered(25)) {
                val count = db.countQueuedBooks()
                if (count > 50) registerAndSignal(25)
            }
            if (!isRegistered(26)) {
                val count = db.countQueuedBooks()
                if (count > 100) registerAndSignal(26)
            }
            if (!isRegistered(30)) {
                val oldestUpload = db.selectOldestUpload()
                if (oldestUpload > 0) {
                    val maxDiff =
                        (now - oldestUpload) / (365L * 24 * 60 * 60 * 1000) // Years
                    if (maxDiff >= 10) registerAndSignal(30)
                }
            }
        } finally {
            db.cleanup()
        }
    }

    fun trigger(id: Int) {
        if (isRegistered(id)) return
        registerAndSignal(id)
    }

    private fun registerAndSignal(id: Int) {
        Timber.d("ACHIEVEMENT $id UNLOCKED")
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

    fun isRegistered(id: Int): Boolean {
        return isRegistered(id, storageCache)
    }

    private fun isRegistered(id: Int, storage: ULong): Boolean {
        if (id > 63) throw Exception("The app doesn't support more than 64 different achievements")
        val storageId = 2f.pow(id).toULong()
        return (storage and storageId > 0UL)
    }
}