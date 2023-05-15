package me.devsaki.hentoid.database

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BiConsumer
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.Group
import me.devsaki.hentoid.database.domains.GroupItem
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Grouping
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.util.ContentHelper
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.workers.UpdateJsonWorker
import me.devsaki.hentoid.workers.data.UpdateJsonData
import timber.log.Timber

object DatabaseMaintenanceK {
    /**
     * Clean up and upgrade database
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    fun getPreLaunchCleanupTasks(): List<BiConsumer<Context, (Float) -> Unit>> {
        return listOf(
            this::setDefaultPropertiesOneShot,
            this::cleanContent,
            this::cleanPropertiesOneShot1,
            this::cleanPropertiesOneShot2,
            this::cleanPropertiesOneShot3,
            this::cleanPropertiesOneShot4,
            this::renameEmptyChapters,
            this::computeContentSize,
            this::createGroups,
            this::computeReadingProgress,
            this::reattachGroupCovers
        )
    }

    fun getPostLaunchCleanupTasks(): List<BiConsumer<Context, (Float) -> Unit>> {
        return listOf(
            this::clearTempContent,
            this::cleanBookmarksOneShot,
            this::cleanOrphanAttributes,
            this::refreshJsonForSecondDownloadDate
        )
    }

    private fun cleanContent(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Set items that were being downloaded in previous session as paused
            Timber.i("Updating queue status : start")
            db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED)
            Timber.i("Updating queue status : done")

            // Unflag all books marked for deletion
            Timber.i("Unflag books : start")
            var contentList = DBHelper.safeFind(db.selectAllFlaggedBooksQ())
            Timber.i("Unflag books : %s books detected", contentList.size)
            db.flagContentsForDeletion(contentList, false)
            Timber.i("Unflag books : done")

            // Unflag all books signaled as being deleted
            Timber.i("Unmark books as being processed : start")
            contentList = DBHelper.safeFind(db.selectAllProcessedBooksQ())
            Timber.i("Unmark books as being processed : %s books detected", contentList.size)
            db.markContentsAsBeingProcessed(contentList, false)
            Timber.i("Unmark books as being processed : done")

            // Add back in the queue isolated DOWNLOADING or PAUSED books that aren't in the queue (since version code 106 / v1.8.0)
            Timber.i("Moving back isolated items to queue : start")
            val contents = db.selectContentByStatus(StatusContent.PAUSED)
            val queueContents = db.selectQueueContents()
            contents.removeAll(queueContents)
            Timber.i("Moving back isolated items to queue : %s books detected", contents.size)
            if (contents.isNotEmpty()) {
                var queueMaxPos = db.selectMaxQueueOrder().toInt()
                val max = contents.size
                var pos = 1f
                for (c in contents) {
                    db.insertQueue(c.id, ++queueMaxPos)
                    emitter(pos++ / max)
                }
            }
            Timber.i("Moving back isolated items to queue : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun clearTempContent(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Clear temporary books created from browsing a book page without downloading it (since versionCode 60 / v1.3.7)
            Timber.i("Clearing temporary books : start")
            val contents = db.selectContentByStatus(StatusContent.SAVED)
            Timber.i("Clearing temporary books : %s books detected", contents.size)
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                db.deleteContentById(c.id)
                emitter(pos++ / max)
            }
            Timber.i("Clearing temporary books : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun cleanPropertiesOneShot1(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Update URLs from deprecated Pururin image hosts
            Timber.i("Upgrading Pururin image hosts : start")
            val contents = db.selectContentWithOldPururinHost()
            Timber.i("Upgrading Pururin image hosts : %s books detected", contents.size)
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                c.coverImageUrl = c.coverImageUrl.replace(
                    "api.pururin.to/images/",
                    "cdn.pururin.to/assets/images/data/"
                )
                if (c.imageFiles != null) for (i in c.imageFiles!!) {
                    db.updateImageFileUrl(
                        i.setUrl(
                            i.url.replace(
                                "api.pururin.to/images/",
                                "cdn.pururin.to/assets/images/data/"
                            )
                        )
                    )
                }
                db.insertContentCore(c)
                emitter(pos++ / max)
            }
            Timber.i("Upgrading Pururin image hosts : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun cleanPropertiesOneShot2(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Update URLs from deprecated Tsumino image covers
            Timber.i("Upgrading Tsumino covers : start")
            val contents = db.selectContentWithOldTsuminoCovers()
            Timber.i("Upgrading Tsumino covers : %s books detected", contents.size)
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                var url = c.coverImageUrl.replace(
                    "www.tsumino.com/Image/Thumb",
                    "content.tsumino.com/thumbs"
                )
                if (!url.endsWith("/1")) url += "/1"
                c.coverImageUrl = url
                db.insertContentCore(c)
                emitter(pos++ / max)
            }
            Timber.i("Upgrading Tsumino covers : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun cleanPropertiesOneShot3(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Update URLs from deprecated Hitomi image covers
            Timber.i("Upgrading Hitomi covers : start")
            val contents = db.selectContentWithOldHitomiCovers()
            Timber.i("Upgrading Hitomi covers : %s books detected", contents.size)
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                val url =
                    c.coverImageUrl.replace("/smallbigtn/", "/webpbigtn/").replace(".jpg", ".webp")
                c.coverImageUrl = url
                db.insertContentCore(c)
                emitter(pos++ / max)
            }
            Timber.i("Upgrading Hitomi covers : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun cleanPropertiesOneShot4(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Update URLs from deprecated Hitomi image covers
            Timber.i("Fixing M18 covers : start")
            var contents = db.selectDownloadedM18Books()
            contents = contents.filter { c -> isM18WrongCover(c) }
            Timber.i("Fixing M18 covers : %s books detected", contents.size)
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                val images: MutableList<ImageFile> = c.imageList.toMutableList()
                val newCover =
                    ImageFile.newCover(c.coverImageUrl, StatusContent.ONLINE).setContentId(c.id)
                images.add(0, newCover)
                images[1].setIsCover(false)
                db.insertImageFiles(images)
                emitter(pos++ / max)
            }
            Timber.i("Fixing M18 covers : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun isM18WrongCover(c: Content): Boolean {
        val images: List<ImageFile> = c.imageList
        if (images.isEmpty()) return false
        val cover = images.firstOrNull { i -> i.isCover }
        return (null == cover || cover.order == 1 && cover.url != c.coverImageUrl)
    }

    private fun renameEmptyChapters(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Update URLs from deprecated Hitomi image covers
            Timber.i("Empying empty chapters : start")
            val chapters = db.selecChaptersEmptyName()
            Timber.i("Empying empty chapters : %s chapters detected", chapters.size)
            val max = chapters.size
            var pos = 1f
            for (c in chapters) {
                c.name = "Chapter " + (c.order + 1) // 0-indexed
                emitter(pos++ / max)
            }
            db.insertChapters(chapters)
            Timber.i("Empying empty chapters : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun cleanBookmarksOneShot(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Detect duplicate bookmarks (host/someurl and host/someurl/)
            Timber.i("Detecting duplicate bookmarks : start")
            db.selectAllDuplicateBookmarksQ().use { entries ->
                Timber.i("Detecting duplicate bookmarks : %d bookmarks detected", entries.count())
                entries.remove()
            }
            Timber.i("Detecting duplicate bookmarks : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun setDefaultPropertiesOneShot(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Set default values for new ObjectBox properties that are values as null by default (see https://github.com/objectbox/objectbox-java/issues/157)
            Timber.i("Set default ObjectBox properties : start")
            var contents = db.selectContentWithNullCompleteField()
            Timber.i(
                "Set default value for Content.complete field : %s items detected",
                contents.size
            )
            var max = contents.size
            var pos = 1f
            for (c in contents) {
                c.isCompleted = false
                db.updateContentObject(c)
                emitter(pos++ / max)
            }
            contents = db.selectContentWithNullDlModeField()
            Timber.i(
                "Set default value for Content.downloadMode field : %s items detected",
                contents.size
            )
            max = contents.size
            pos = 1f
            for (c in contents) {
                c.downloadMode = Content.DownloadMode.DOWNLOAD
                db.updateContentObject(c)
                emitter(pos++ / max)
            }
            contents = db.selectContentWithNullMergeField()
            Timber.i(
                "Set default value for Content.manuallyMerged field : %s items detected",
                contents.size
            )
            max = contents.size
            pos = 1f
            for (c in contents) {
                c.isManuallyMerged = false
                db.updateContentObject(c)
                emitter(pos++ / max)
            }
            contents = db.selectContentWithNullDlCompletionDateField()
            Timber.i(
                "Set default value for Content.downloadCompletionDate field : %s items detected",
                contents.size
            )
            max = contents.size
            pos = 1f
            for (c in contents) {
                if (ContentHelper.isInLibrary(c.status)) c.downloadCompletionDate =
                    c.downloadDate else c.downloadCompletionDate =
                    0
                db.updateContentObject(c)
                emitter(pos++ / max)
            }
            contents = db.selectContentWithInvalidUploadDate()
            Timber.i("Fixing invalid upload dates : %s items detected", contents.size)
            max = contents.size
            pos = 1f
            for (c in contents) {
                c.uploadDate = c.uploadDate * 1000
                db.updateContentObject(c)
                emitter(pos++ / max)
            }
            val chapters = db.selectChapterWithNullUploadDate()
            Timber.i(
                "Set default value for Chapter.uploadDate field : %s items detected",
                chapters.size
            )
            max = chapters.size
            pos = 1f
            for (c in chapters) {
                c.uploadDate = 0
                emitter(pos++ / max)
            }
            db.insertChapters(chapters)
            Timber.i("Set default ObjectBox properties : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun computeContentSize(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Computing downloaded content size : start")
            val contents = db.selectDownloadedContentWithNoSize()
            Timber.i("Computing downloaded content size : %s books detected", contents.size)
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                c.computeSize()
                db.insertContentCore(c)
                emitter(pos++ / max)
            }
            Timber.i("Computing downloaded content size : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun createGroups(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Create non-existing groupings : start")
            val groupingsToProcess: MutableList<Grouping> = ArrayList()
            for (grouping in arrayOf(
                Grouping.ARTIST,
                Grouping.DL_DATE
            )) if (0L == db.countGroupsFor(grouping)) groupingsToProcess.add(grouping)

            // Test the existence of the "Ungrouped" custom group
            val ungroupedCustomGroup = DBHelper.safeFind(
                db.selectGroupsQ(
                    Grouping.CUSTOM.id,
                    null,
                    -1,
                    false,
                    1,
                    false,
                    -1
                )
            )
            if (ungroupedCustomGroup.isEmpty()) groupingsToProcess.add(Grouping.CUSTOM)
            Timber.i(
                "Create non-existing groupings : %s non-existing groupings detected",
                groupingsToProcess.size
            )
            var bookInsertCount = 0
            val toInsert: MutableList<Triple<Group, Attribute?, List<Long>>> = ArrayList()
            val res = context.resources
            for (g in groupingsToProcess) {
                when (g) {
                    Grouping.ARTIST -> {
                        val artists = db.selectAvailableAttributes(
                            AttributeType.ARTIST,
                            -1,
                            LongArray(0),
                            null,
                            ContentHelper.Location.ANY,
                            ContentHelper.Type.ANY,
                            false,
                            null,
                            Preferences.Constant.SEARCH_ORDER_ATTRIBUTES_ALPHABETIC,
                            0,
                            0
                        )
                        artists.addAll(
                            db.selectAvailableAttributes(
                                AttributeType.CIRCLE,
                                -1,
                                LongArray(0),
                                null,
                                ContentHelper.Location.ANY,
                                ContentHelper.Type.ANY,
                                false,
                                null,
                                Preferences.Constant.SEARCH_ORDER_ATTRIBUTES_ALPHABETIC,
                                0,
                                0
                            )
                        )
                        var order = 1
                        for (a in artists) {
                            val group = Group(Grouping.ARTIST, a.name, order++)
                            group.setSubtype(if (a.type == AttributeType.ARTIST) Preferences.Constant.ARTIST_GROUP_VISIBILITY_ARTISTS else Preferences.Constant.ARTIST_GROUP_VISIBILITY_GROUPS)
                            if (!a.contents.isEmpty()) group.coverContent.target = a.contents[0]
                            bookInsertCount += a.contents.size
                            toInsert.add(
                                Triple(group, a, a.contents.map { c -> c.id })
                            )
                        }
                    }

                    Grouping.DL_DATE -> {
                        var group = Group(Grouping.DL_DATE, res.getString(R.string.group_today), 1)
                        group.propertyMin = 0
                        group.propertyMax = 1
                        toInsert.add(Triple(group, null, emptyList()))
                        group = Group(Grouping.DL_DATE, res.getString(R.string.group_7), 2)
                        group.propertyMin = 1
                        group.propertyMax = 8
                        toInsert.add(Triple(group, null, emptyList()))
                        group = Group(Grouping.DL_DATE, res.getString(R.string.group_30), 3)
                        group.propertyMin = 8
                        group.propertyMax = 31
                        toInsert.add(Triple(group, null, emptyList()))
                        group = Group(Grouping.DL_DATE, res.getString(R.string.group_60), 4)
                        group.propertyMin = 31
                        group.propertyMax = 61
                        toInsert.add(Triple(group, null, emptyList()))
                        group = Group(Grouping.DL_DATE, res.getString(R.string.group_year), 5)
                        group.propertyMin = 61
                        group.propertyMax = 366
                        toInsert.add(Triple(group, null, emptyList()))
                        group = Group(Grouping.DL_DATE, res.getString(R.string.group_long), 6)
                        group.propertyMin = 366
                        group.propertyMax = 9999999
                        toInsert.add(Triple(group, null, emptyList()))
                    }

                    Grouping.CUSTOM -> {
                        val group = Group(
                            Grouping.CUSTOM,
                            res.getString(R.string.group_no_group),
                            1
                        ).setSubtype(1)
                        toInsert.add(Triple(group, null, emptyList()))
                    }

                    else -> {
                        // Nothing there
                    }
                }
            }

            // Actual insert is inside its dedicated loop to allow displaying a proper progress bar
            Timber.i("Create non-existing groupings : %s relations to create", bookInsertCount)
            var pos = 1f
            for (data in toInsert) {
                db.insertGroup(data.first)
                data.second?.putGroup(data.first)
                for ((order, contentId) in data.third.withIndex()) {
                    val item = GroupItem(contentId, data.first, order)
                    db.insertGroupItem(item)
                    emitter(pos++ / bookInsertCount)
                }
            }
            Timber.i("Create non-existing groupings : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun computeReadingProgress(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Computing downloaded content read progress : start")
            val contents = db.selectDownloadedContentWithNoReadProgress()
            Timber.i(
                "Computing downloaded content read progress : %s books detected",
                contents.size
            )
            val max = contents.size
            var pos = 1f
            for (c in contents) {
                c.computeReadProgress()
                db.insertContentCore(c)
                emitter(pos++ / max)
            }
            Timber.i("Computing downloaded content read progress : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun reattachGroupCovers(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Reattaching group covers : start")
            val groups = db.selectGroupsWithNoCoverContent()
            Timber.i("Reattaching group covers : %s groups detected", groups.size)
            val max = groups.size
            var pos = 1f
            for (g in groups) {
                val contentIds = g.contentIds
                if (contentIds.isNotEmpty()) {
                    g.coverContent.targetId = contentIds[0]
                    db.insertGroup(g)
                }
                emitter(pos++ / max)
            }
            Timber.i("Reattaching group covers : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun cleanOrphanAttributes(context: Context, emitter: (Float) -> Unit) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Compute missing downloaded Content size according to underlying ImageFile sizes
            Timber.i("Cleaning orphan attributes : start")
            db.cleanupOrphanAttributes()
            Timber.i("Cleaning orphan attributes : done")
        } finally {
            db.closeThreadResources()
        }
    }

    private fun refreshJsonForSecondDownloadDate(
        context: Context,
        emitter: (Float) -> Unit
    ) {
        val db = ObjectBoxDB.getInstance(context)
        try {
            // Refresh JSONs to persist missing downloadCompletionDates
            if (!Preferences.isRefreshJson1Complete()) {
                Timber.i("Refresh Json for second download date : start")
                val contentToRefresh = db.selectContentIdsWithUpdatableJson()
                Timber.i(
                    "Refresh Json for second download date : %d books detected",
                    contentToRefresh.size
                )
                if (contentToRefresh.isNotEmpty()) {
                    val builder = UpdateJsonData.Builder()
                    builder.setUpdateMissingDlDate(true) // Setting all book IDs might break the data size limit for large collections
                    val workManager = WorkManager.getInstance(context)
                    workManager.enqueueUniqueWork(
                        R.id.udpate_json_service.toString(),
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        OneTimeWorkRequestBuilder<UpdateJsonWorker>()
                            .setInputData(builder.data)
                            .build()
                    )
                }
                Timber.i("Refresh Json for second download date : done")
                Preferences.setIsRefreshJson1Complete(true)
            }
        } finally {
            db.closeThreadResources()
        }
    }
}