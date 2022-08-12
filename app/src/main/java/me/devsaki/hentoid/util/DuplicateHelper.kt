package me.devsaki.hentoid.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.util.Consumer
import com.annimon.stream.function.BiConsumer
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.util.file.FileHelper
import me.devsaki.hentoid.util.image.ImageHelper
import me.devsaki.hentoid.util.image.ImagePHash
import me.devsaki.hentoid.util.string_similarity.StringSimilarity
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class DuplicateHelper {

    companion object {
        // Thresholds according to the "sensibility" setting
        private val COVER_THRESHOLDS =
            doubleArrayOf(0.8, 0.85, 0.9) // @48-bit resolution, according to calibration tests
        private val TEXT_THRESHOLDS = doubleArrayOf(0.78, 0.8, 0.85)
        private val TOTAL_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
        private const val COVER_WORK_RESOLUTION = 48

        private val TITLE_CHAPTER_WORDS = listOf(
            "chapter",
            "chap",
            "case",
            "after",
            "before",
            "prologue",
            "prelude",
            "final",
            "part",
            "update",
            "gaiden",
            "issue",
            "volume",
            "vol",
            "first",
            "second",
            "third",
            "fourth",
            "fifth",
            "1st",
            "2nd",
            "3rd",
            "4th",
            "5th",
            "zenpen",
            "全編",
            "chuuhen",
            "中編",
            "kouhen",
            "後編",
            "ex",
            // Circled numerals (yes, some books do use them)
            "①",
            "②",
            "③",
            "④",
            "⑤",
            // Roman numerals (yes, some books do use them)
            "i",
            "v",
            "x",
        )


        fun getHashEngine(): ImagePHash {
            return getHashEngine(COVER_WORK_RESOLUTION)
        }

        fun getHashEngine(resolution: Int = COVER_WORK_RESOLUTION): ImagePHash {
            return ImagePHash(resolution, 8)
        }

        fun indexCovers(
            context: Context,
            dao: CollectionDAO,
            stopped: AtomicBoolean,
            info: Consumer<Content>,
            progress: BiConsumer<Int, Int>,
            error: Consumer<Throwable>
        ) {
            val hashEngine = getHashEngine()
            val contentToIndex = dao.selectContentWithUnhashedCovers()
            val nbContent = contentToIndex.size

            for ((index, c) in contentToIndex.withIndex()) {
                try {
                    info.accept(c)
                    indexContent(context, dao, c, hashEngine)
                    progress.accept(index + 1, nbContent)
                } catch (t: Throwable) {
                    // Don't break the loop
                    error.accept(t)
                }
                if (stopped.get()) break
            }
            progress.accept(nbContent, nbContent)
        }

        private fun indexContent(
            context: Context,
            dao: CollectionDAO,
            content: Content,
            hashEngine: ImagePHash,
        ) {
            val bitmap = getCoverBitmapFromContent(context, content)
            val pHash = calcPhash(hashEngine, bitmap)
            bitmap?.recycle()
            savePhash(context, dao, content, pHash)
        }

        fun getCoverBitmapFromContent(context: Context, content: Content): Bitmap? {
            if (content.cover.fileUri.isEmpty()) return null

            try {
                FileHelper.getInputStream(context, Uri.parse(content.cover.fileUri))
                    .use {
                        return getCoverBitmapFromStream(it)
                    }
            } catch (e: IOException) {
                Timber.w(e) // Doesn't break the loop
                return null
            }
        }

        fun getCoverBitmapFromStream(stream: InputStream): Bitmap? {
            return ImageHelper.decodeSampledBitmapFromStream(
                stream,
                COVER_WORK_RESOLUTION,
                COVER_WORK_RESOLUTION
            )
        }

        fun calcPhash(hashEngine: ImagePHash, bitmap: Bitmap?): Long {
            return if (null == bitmap) Long.MIN_VALUE
            else hashEngine.calcPHash(bitmap)
        }

        private fun savePhash(context: Context, dao: CollectionDAO, content: Content, pHash: Long) {
            content.cover.imageHash = pHash
            // Update the picture in DB
            dao.insertImageFile(content.cover)
            // The following block has to be abandoned if the cost of retaining all Content in memory is too high
            try {
                // Update the book JSON if the book folder still exists
                if (content.storageUri.isNotEmpty()) {
                    val folder = FileHelper.getFolderFromTreeUriString(context, content.storageUri)
                    if (folder != null) {
                        if (content.jsonUri.isNotEmpty()) ContentHelper.updateJson(
                            context,
                            content
                        )
                        else ContentHelper.createJson(context, content)
                    }
                }
            } catch (e: IOException) {
                Timber.w(e) // Doesn't break the loop
            }
        }

        fun processContent(
            reference: DuplicateCandidate,
            candidate: DuplicateCandidate,
            useTitle: Boolean,
            useCover: Boolean,
            useSameArtist: Boolean,
            useSameLanguage: Boolean,
            ignoreChapters: Boolean,
            sensitivity: Int,
            textComparator: StringSimilarity
        ): DuplicateEntry? {
            var titleScore = -1f
            var coverScore = -1f
            var artistScore = -1f

            // Remove if not same language
            if (useSameLanguage && !containsSameLanguage(
                    reference.countryCodes,
                    candidate.countryCodes
                )
            ) return null
            if (useCover) {
                coverScore = computeCoverScore(
                    reference.coverHash, candidate.coverHash,
                    sensitivity
                )
                // Ignored cover
                if (coverScore == -2f) return null
            }
            if (useTitle) titleScore = computeTitleScore(
                textComparator,
                reference.titleCleanup, reference.titleNoDigits,
                candidate.titleCleanup, candidate.titleNoDigits,
                ignoreChapters,
                sensitivity
            )
            if (useSameArtist) artistScore =
                computeArtistScore(reference.artistsCleanup, candidate.artistsCleanup)
            val result = DuplicateEntry(
                reference.id,
                reference.size,
                candidate.id,
                candidate.size,
                titleScore,
                coverScore,
                artistScore,
                0
            )
            return if (result.calcTotalScore() >= TOTAL_THRESHOLDS[sensitivity]) result else null
        }


        private fun containsSameLanguage(
            referenceCodes: List<String>?,
            candidateCodes: List<String>?
        ): Boolean {
            if (!referenceCodes.isNullOrEmpty() && !candidateCodes.isNullOrEmpty()) {
                for (refCode in referenceCodes) {
                    if (candidateCodes.contains(refCode)) return true
                }
                return false
            }
            return true
        }

        private fun computeCoverScore(
            referenceHash: Long,
            candidateHash: Long,
            sensitivity: Int
        ): Float {
            // Don't analyze anything if covers have not been hashed (will be done on next iteration)
            if (0L == referenceHash || 0L == candidateHash) return -2f
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
            ignoreChapters: Boolean,
            sensitivity: Int
        ): Float {
            val similarity1 =
                textComparator.similarity(referenceTitleCleanup, candidateTitleCleanup)
            if (ignoreChapters) {
                // Perfect match
                if (similarity1 > 0.995) return similarity1.toFloat()
                // Other cases : check if both titles are chapters or sequels
                return if (similarity1 > TEXT_THRESHOLDS[sensitivity]) {
                    val similarity2 =
                        textComparator.similarity(referenceTitleNoDigits, candidateTitleNoDigits)
                    // Cleaned up versions are identical
                    // => most probably a chapter variant -> set to 0%
                    if (similarity2 > similarity1 && similarity2 > 0.995) return 0f
                    // Very little difference between cleaned up and original version
                    // => not a chapter variant
                    if (similarity2 - similarity1 < 0.01) {
                        similarity1.toFloat()
                    } else {
                        0f // Most probably a chapter variant -> set to 0%
                    }
                } else {
                    0f // Below threshold
                }
            } else return if (similarity1 >= TEXT_THRESHOLDS[sensitivity]) similarity1.toFloat() else 0f
        }

        fun sanitizeTitle(title: String): String {
            var result = StringHelper.removeDigits(title)
            for (s in TITLE_CHAPTER_WORDS) result = result.replace(s, "")
            return result
        }

        private fun computeArtistScore(
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
        useLanguage: Boolean,
        forceCoverHash: Long = Long.MIN_VALUE
    ) {
        val id = content.id
        val coverHash =
            if (Long.MIN_VALUE == forceCoverHash) content.cover.imageHash else forceCoverHash
        val size = content.size
        val titleCleanup = (if (useTitle) StringHelper.cleanup(content.title) else "")!!
        val titleNoDigits = if (useTitle) sanitizeTitle(titleCleanup) else ""
        val artistsCleanup: List<String>? =
            if (useArtist) content.attributeMap[AttributeType.ARTIST]?.map { it ->
                StringHelper.cleanup(it.name)
            } else Collections.emptyList()
        val countryCodes = if (useLanguage) content.attributeMap[AttributeType.LANGUAGE]?.map {
            LanguageHelper.getCountryCodeFromLanguage(it.name)
        } else Collections.emptyList()
    }

}