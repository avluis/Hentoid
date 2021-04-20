package me.devsaki.hentoid.workers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.Data
import androidx.work.WorkerParameters
import info.debatty.java.stringsimilarity.Cosine
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.ObjectBoxDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.notification.duplicates.DuplicateStartNotification
import me.devsaki.hentoid.util.*
import me.devsaki.hentoid.util.notification.Notification
import me.devsaki.hentoid.workers.data.DuplicateData
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException

/**
 * Worker responsible for detecting duplicates
 */
class DuplicateDetectorWorker(context: Context, parameters: WorkerParameters) : BaseWorker(context, parameters, R.id.duplicate_detector_service) {

    companion object {
        // Processing steps
        const val STEP_COVER_INDEX = 0
        const val STEP_DUPLICATES = 1

        // Thresholds according to the "sensibility" setting
        private val COVER_THRESHOLDS = doubleArrayOf(0.71, 0.75, 0.8) // @48-bit resolution, according to calibration tests
        private val TEXT_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
        private val TOTAL_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
    }

    val dao = ObjectBoxDAO(context)

    override fun getStartNotification(): Notification {
        return DuplicateStartNotification()
    }

    override fun onClear() {
        dao.cleanup()
    }

    override fun getToWork(input: Data) {
        val data = DuplicateData.Parser(inputData)

        val library = dao.selectStoredBooks(false, false, Preferences.Constant.ORDER_FIELD_SIZE, true)
        if (data.useCover) indexCovers(library)
        processLibrary(library, data.useTitle, data.useCover, data.useArtist, data.useSameLanguage, data.sensitivity)
    }

    /**
     * Detect if there are missing cover hashes
     */
    private fun indexCovers(library: List<Content>) {
        val noCoverHashes = library.filter { 0L == it.cover.imageHash }.map { it.cover }
        if (noCoverHashes.isNotEmpty()) {
            val hash = ImagePHash(48, 8)

            EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, 0, 0, noCoverHashes.size))
            var elementsKO = 0
            for ((progress, img) in noCoverHashes.withIndex()) {
                try {
                    FileHelper.getInputStream(applicationContext, Uri.parse(img.fileUri))
                            .use {
                                val b = BitmapFactory.decodeStream(it)
                                img.imageHash = hash.calcPHash(b)
                            }
                } catch (e: IOException) {
                    elementsKO++
                    Timber.w(e) // Doesn't break the loop
                }
                EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, progress - elementsKO + 1, elementsKO, noCoverHashes.size))
                Timber.i("Calculating hashes : %s / %s", progress + 1, noCoverHashes.size)
            }
            EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_COVER_INDEX, noCoverHashes.size - elementsKO, elementsKO, noCoverHashes.size))
            dao.insertImageFiles(noCoverHashes)
        }
    }

    private fun processLibrary(
            library: List<Content>,
            useTitle: Boolean,
            useCover: Boolean,
            useArtist: Boolean,
            sameLanguageOnly: Boolean,
            sensitivity: Int
    ) {
        Helper.assertNonUiThread()
        val detectedDuplicatesHash = HashMap<Pair<Long, Long>, DuplicateResult>()
        val result = ArrayList<DuplicateResult>()
        val textComparator = Cosine()

        EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_DUPLICATES, 0, 0, library.size))
        for ((progress, contentRef) in library.withIndex()) {
            lateinit var referenceTitleDigits: String
            lateinit var referenceTitle: String
            if (useTitle) {
                referenceTitleDigits = StringHelper.cleanup(contentRef.title)
                referenceTitle = StringHelper.removeDigits(referenceTitleDigits)
            }

            for (contentCandidate in library) {
                // Ignore same item comparison
                if (contentRef.id == contentCandidate.id) continue

                // Check if that combination has already been processed
                val existingResult = detectedDuplicatesHash[Pair(contentCandidate.id, contentRef.id)]
                if (existingResult?.duplicate != null) {
                    result.add(DuplicateResult(
                            existingResult.duplicate,
                            existingResult.reference,
                            existingResult.titleScore,
                            existingResult.coverScore,
                            existingResult.artistScore))
                    continue
                }

                // Process current combination of Content
                var titleScore = -1f
                var coverScore = -1f
                var artistScore = -1f

                // Remove if not same language
                if (sameLanguageOnly && !containsSameLanguage(contentRef, contentCandidate)) {
                    val duplicateResult = DuplicateResult(contentRef, contentCandidate, titleScore, coverScore, artistScore)
                    result.add(duplicateResult)
                    detectedDuplicatesHash[Pair(contentRef.id, contentCandidate.id)] = duplicateResult
                    continue
                }

                if (useCover) {
                    val preCoverScore = ImagePHash.similarity(contentRef.cover.imageHash, contentCandidate.cover.imageHash)
                    coverScore = if (preCoverScore >= COVER_THRESHOLDS[sensitivity]) preCoverScore else 0f
                }

                if (useTitle) titleScore = computeTitleScore(textComparator, referenceTitleDigits, referenceTitle, contentCandidate, sensitivity)

                if (useArtist) artistScore = computeArtistScore(contentRef, contentCandidate)

                val duplicateResult = DuplicateResult(contentRef, contentCandidate, titleScore, coverScore, artistScore)
                result.add(duplicateResult)
                detectedDuplicatesHash[Pair(contentRef.id, contentCandidate.id)] = duplicateResult
            }
            EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, progress + 1, 0, library.size))
        }

        EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_COVER_INDEX, library.size, 0, library.size))
        val finalResult = result.filter { it.calcTotalScore() >= TOTAL_THRESHOLDS[sensitivity] }
        // TODO save to DB
    }

    private fun containsSameLanguage(contentRef: Content, contentCandidate: Content): Boolean {
        val refLanguages = contentRef.attributeMap[AttributeType.LANGUAGE]
        val candidateLanguages = contentCandidate.attributeMap[AttributeType.LANGUAGE]
        if (!candidateLanguages.isNullOrEmpty() && !refLanguages.isNullOrEmpty()) {
            val candidateCodes = candidateLanguages.map { LanguageHelper.getCountryCodeFromLanguage(it.name) }
            val refCodes = refLanguages.map { LanguageHelper.getCountryCodeFromLanguage(it.name) }

            for (refCode in refCodes) {
                if (candidateCodes.contains(refCode)) return true
            }
            return false
        }
        return true
    }

    private fun computeTitleScore(
            textComparator: StringSimilarity,
            referenceTitleDigits: String,
            referenceTitle: String,
            contentCandidate: Content,
            sensitivity: Int
    ): Float {
        var candidateTitle = StringHelper.cleanup(contentCandidate.title)
        val similarity1 = textComparator.similarity(referenceTitleDigits, candidateTitle).toFloat()
        return if (similarity1 > TEXT_THRESHOLDS[sensitivity]) {
            candidateTitle = StringHelper.removeDigits(candidateTitle)
            val similarity2 = textComparator.similarity(referenceTitle, candidateTitle)
            if (similarity2 - similarity1 < 0.02) {
                similarity1
            } else {
                0f // Most probably a chapter variant -> set to 0%
            }
        } else {
            0f // Below threshold
        }
    }

    private fun computeArtistScore(
            contentReference: Content,
            contentCandidate: Content
    ): Float {
        val refArtists = contentReference.attributeMap[AttributeType.ARTIST]
        val candidateArtists = contentCandidate.attributeMap[AttributeType.ARTIST]
        if (!candidateArtists.isNullOrEmpty() && !refArtists.isNullOrEmpty()) {
            for (candidateArtist in candidateArtists) {
                for (refArtist in refArtists) {
                    if (candidateArtist.id == refArtist.id) return 1f
                    if (refArtist.equals(candidateArtist)) return 1f
                    if (StringHelper.isTransposition(refArtist.name, candidateArtist.name)) return 1f
                }
            }
            return 0f // No match
        }
        return -1f // Nothing to match against
    }

    class DuplicateResult(
            val reference: Content,
            val duplicate: Content? = null,
            val titleScore: Float = 0f,
            val coverScore: Float = 0f,
            val artistScore: Float = 0f) {

        private var totalScore = -1f
        var nbDuplicates = 1

        fun calcTotalScore(): Float {
            if (totalScore > -1) return totalScore
            // Calculate
            val operands = ArrayList<android.util.Pair<Float, Float>>()
            if (titleScore > -1) operands.add(android.util.Pair<Float, Float>(titleScore, 1f))
            if (coverScore > -1) operands.add(android.util.Pair<Float, Float>(coverScore, 1f))
            if (artistScore > -1) operands.add(android.util.Pair<Float, Float>(artistScore, 0.5f))
            return Helper.weigthedAverage(operands)
        }

        fun hash64(): Long {
            return Helper.hash64((reference.id.toString() + "." + duplicate?.id.toString()).toByteArray())
        }
    }
}