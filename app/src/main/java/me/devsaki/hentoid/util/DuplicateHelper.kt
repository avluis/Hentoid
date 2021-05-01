package me.devsaki.hentoid.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.util.Consumer
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.string_similarity.StringSimilarity
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.IOException
import java.util.*

class DuplicateHelper {

    companion object {
        // Thresholds according to the "sensibility" setting
        private val COVER_THRESHOLDS = doubleArrayOf(0.71, 0.75, 0.8) // @48-bit resolution, according to calibration tests
        private val TEXT_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
        private const val COVER_WORK_RESOLUTION = 48


        fun getHashEngine(): ImagePHash {
            return ImagePHash(COVER_WORK_RESOLUTION, 8)
        }

        fun indexCoversRx(
                context: Context,
                dao: CollectionDAO,
                progress: Consumer<Float>): Disposable {

            val hash = getHashEngine()
            var index = 0
            val nbContent = dao.countContentWithUnhashedCovers()

            return dao.streamContentWithUnhashedCovers()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .map { content -> Pair(content, getCoverBitmapFromContent(context, content)) }
                    .observeOn(Schedulers.computation())
                    .map {
                        val pHash = calcPhash(hash, it.second)
                        it.second?.recycle()
                        Pair(it.first, pHash)
                    }
                    .observeOn(Schedulers.io())
                    .map { contentHash -> savePhash(context, dao, contentHash.first, contentHash.second) }
                    .subscribeBy(
                            onNext = { progress.accept(++index * 1f / nbContent) },
                            onError = { t -> Timber.w(t) },
                            onComplete = { progress.accept(1f) }
                    )

        }

        fun getCoverBitmapFromContent(context: Context, content: Content): Bitmap? {
            if (content.cover.fileUri.isEmpty()) return null

            try {
                BufferedInputStream(FileHelper.getInputStream(context, Uri.parse(content.cover.fileUri)))
                        .use {
                            return ImageHelper.decodeSampledBitmapFromStream(it, COVER_WORK_RESOLUTION, COVER_WORK_RESOLUTION)
                        }
            } catch (e: IOException) {
                Timber.w(e) // Doesn't break the loop
                return null
            }
        }

        fun calcPhash(hashEngine: ImagePHash, bitmap: Bitmap?): Long {
            return if (null == bitmap) -1
            else hashEngine.calcPHash(bitmap)
        }

        private fun savePhash(context: Context, dao: CollectionDAO, content: Content, pHash: Long) {
            content.cover.imageHash = pHash
            // Update the picture in DB
            dao.insertImageFile(content.cover)
            try {
                // Update the book JSON if the book folder still exists
                if (content.storageUri.isNotEmpty()) {
                    val folder = FileHelper.getFolderFromTreeUriString(context, content.storageUri)
                    if (folder != null) {
                        if (content.jsonUri.isNotEmpty()) ContentHelper.updateContentJson(context, content)
                        else ContentHelper.createContentJson(context, content)
                    }
                }
            } catch (e: IOException) {
                Timber.w(e) // Doesn't break the loop
            }
        }

        fun containsSameLanguage(referenceCodes: List<String>?, candidateCodes: List<String>?): Boolean {
            if (!referenceCodes.isNullOrEmpty() && !candidateCodes.isNullOrEmpty()) {
                for (refCode in referenceCodes) {
                    if (candidateCodes.contains(refCode)) return true
                }
                return false
            }
            return true
        }

        fun computeCoverScore(
                referenceHash: Long,
                candidateHash: Long,
                sensitivity: Int
        ): Float {
            // Don't analyze anything if covers have not been hashed (will be done on next iteration)
            if (0L == referenceHash || 0L == candidateHash) return -1f
            // Ignore unhashable covers
            if (Long.MIN_VALUE == referenceHash || Long.MIN_VALUE == candidateHash) return -1f

            val preCoverScore = ImagePHash.similarity(referenceHash, candidateHash)
            return if (preCoverScore >= COVER_THRESHOLDS[sensitivity]) preCoverScore else 0f
        }

        fun computeTitleScore(
                textComparator: StringSimilarity,
                referenceTitleCleanup: String,
                referenceTitleNoDigits: String,
                candidateTitleCleanup: String,
                candidateTitleNoDigits: String,
                sensitivity: Int
        ): Float {
            val similarity1 = textComparator.similarity(referenceTitleCleanup, candidateTitleCleanup).toFloat()
            return if (similarity1 > TEXT_THRESHOLDS[sensitivity]) {
                val similarity2 = textComparator.similarity(referenceTitleNoDigits, candidateTitleNoDigits)
                if (similarity2 - similarity1 < 0.02) {
                    similarity1
                } else {
                    0f // Most probably a chapter variant -> set to 0%
                }
            } else {
                0f // Below threshold
            }
        }

        fun computeArtistScore(
                referenceArtistsCleanup: List<String>?,
                candidateArtistsCleanup: List<String>?
        ): Float {
            if (!candidateArtistsCleanup.isNullOrEmpty() && !referenceArtistsCleanup.isNullOrEmpty()) {
                for (candidateArtist in candidateArtistsCleanup) {
                    for (refArtist in referenceArtistsCleanup) {
                        if (refArtist == candidateArtist) return 1f
                        if (StringHelper.isTransposition(refArtist, candidateArtist)) return 1f
                    }
                }
                return 0f // No match
            }
            return -1f // Nothing to match against
        }
    }

    class DuplicateCandidate(
            content: Content,
            useTitle: Boolean,
            useArtist: Boolean,
            useLanguage: Boolean) {
        val id = content.id
        val coverHash = content.cover.imageHash
        val size = content.size
        val titleCleanup = if (useTitle) StringHelper.cleanup(content.title) else ""
        val titleNoDigits = if (useTitle) StringHelper.removeDigits(titleCleanup) else ""
        val artistsCleanup = if (useArtist) content.attributeMap[AttributeType.ARTIST]?.map { it -> StringHelper.cleanup(it.name) } else Collections.emptyList()
        val countryCodes = if (useLanguage) content.attributeMap[AttributeType.LANGUAGE]?.map { LanguageHelper.getCountryCodeFromLanguage(it.name) } else Collections.emptyList()
    }

}