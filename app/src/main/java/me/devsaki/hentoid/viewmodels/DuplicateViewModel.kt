package me.devsaki.hentoid.viewmodels

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import info.debatty.java.stringsimilarity.Cosine
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.*
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.io.IOException


class DuplicateViewModel(application: Application, val dao: CollectionDAO) : AndroidViewModel(application) {

    companion object {
        // Processing steps
        const val STEP_COVER_INDEX = 0
        const val STEP_DUPLICATES = 1

        // Thresholds according to the "sensibility" setting
        private val COVER_THRESHOLDS = doubleArrayOf(0.71, 0.75, 0.8) // @48-bit resolution, according to calibration tests
        private val TEXT_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
        private val TOTAL_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
    }

    val allDuplicates = MutableLiveData<List<DuplicateResult>>()
    val selectedDuplicates = MutableLiveData<List<DuplicateResult>>()


    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }

    fun scanForDuplicates(
            useTitle: Boolean,
            useCover: Boolean,
            useArtist: Boolean,
            sameLanguageOnly: Boolean,
            sensitivity: Int
    ) {
        var searchDisposable = Disposables.empty()
        searchDisposable = dao.selectStoredBooks(false, false)
                .observeOn(Schedulers.io())
                .map { list -> indexCovers(useCover, list) }
                .observeOn(Schedulers.computation())
                .subscribe { list ->
                    run {
                        processLibrary(list.sortedByDescending { it.size }, useTitle, useCover, useArtist, sameLanguageOnly, sensitivity)
                        searchDisposable.dispose()
                    }
                }
    }

    /**
     * Detect if there are missing cover hashes
     */
    private fun indexCovers(
            useCover: Boolean,
            library: List<Content>): List<Content> {
        if (useCover) {
            val context = getApplication<Application>().applicationContext

            val noCoverHashes = library.filter { 0L == it.cover.imageHash }.map { it.cover }
            if (noCoverHashes.isNotEmpty()) {
                val hash = ImagePHash(48, 8)

                EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, 0, 0, noCoverHashes.size))
                var elementsKO = 0
                for ((progress, img) in noCoverHashes.withIndex()) {
                    try {
                        FileHelper.getInputStream(context, Uri.parse(img.fileUri))
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
        return library
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
                            true,
                            existingResult.titleScore,
                            existingResult.coverScore,
                            existingResult.artistScore))
                    continue
                }

                // Process current combination of Content
                var titleScore = -1.0
                var coverScore = -1.0
                var artistScore = -1.0

                // Remove if not same language
                if (sameLanguageOnly && !containsSameLanguage(contentRef, contentCandidate)) {
                    val duplicateResult = DuplicateResult(contentRef, contentCandidate, false, titleScore, coverScore, artistScore)
                    result.add(duplicateResult)
                    detectedDuplicatesHash[Pair(contentRef.id, contentCandidate.id)] = duplicateResult
                    continue
                }

                if (useCover) {
                    val preCoverScore = ImagePHash.similarity(contentRef.cover.imageHash, contentCandidate.cover.imageHash)
                    coverScore = if (preCoverScore >= COVER_THRESHOLDS[sensitivity]) preCoverScore else 0.0
                }

                if (useTitle) titleScore = computeTitleScore(textComparator, referenceTitleDigits, referenceTitle, contentCandidate, sensitivity)

                if (useArtist) artistScore = computeArtistScore(contentRef, contentCandidate)

                val duplicateResult = DuplicateResult(contentRef, contentCandidate, false, titleScore, coverScore, artistScore)
                result.add(duplicateResult)
                detectedDuplicatesHash[Pair(contentRef.id, contentCandidate.id)] = duplicateResult
            }
            EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.PROGRESS, STEP_COVER_INDEX, progress + 1, 0, library.size))
        }
        // TODO save to DB instead
        EventBus.getDefault().post(ProcessEvent(ProcessEvent.EventType.COMPLETE, STEP_COVER_INDEX, library.size, 0, library.size))
        val finalResult = result.filter { it.calcTotalScore() >= TOTAL_THRESHOLDS[sensitivity] }
        allDuplicates.postValue(finalResult)
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
    ): Double {
        var candidateTitle = StringHelper.cleanup(contentCandidate.title)
        val similarity1 = textComparator.similarity(referenceTitleDigits, candidateTitle)
        return if (similarity1 > TEXT_THRESHOLDS[sensitivity]) {
            candidateTitle = StringHelper.removeDigits(candidateTitle)
            val similarity2 = textComparator.similarity(referenceTitle, candidateTitle)
            if (similarity2 - similarity1 < 0.02) {
                similarity1
            } else {
                0.0 // Most probably a chapter variant -> set to 0%
            }
        } else {
            0.0 // Below threshold
        }
    }

    private fun computeArtistScore(
            contentReference: Content,
            contentCandidate: Content
    ): Double {
        val refArtists = contentReference.attributeMap[AttributeType.ARTIST]
        val candidateArtists = contentCandidate.attributeMap[AttributeType.ARTIST]
        if (!candidateArtists.isNullOrEmpty() && !refArtists.isNullOrEmpty()) {
            for (candidateArtist in candidateArtists) {
                for (refArtist in refArtists) {
                    if (candidateArtist.id == refArtist.id) return 1.0
                    if (refArtist.equals(candidateArtist)) return 1.0
                    if (StringHelper.isTransposition(refArtist.name, candidateArtist.name)) return 1.0
                }
            }
            return 0.0 // No match
        }
        return -1.0 // Nothing to match against
    }

    class DuplicateResult(
            val reference: Content,
            val duplicate: Content? = null,
            val mirrorEntry: Boolean = false,
            val titleScore: Double = 0.0,
            val coverScore: Double = 0.0,
            val artistScore: Double = 0.0) {

        private var totalScore = -1.0
        var nbDuplicates = 1

        fun calcTotalScore(): Double {
            if (totalScore > -1.0) return totalScore
            // Calculate
            val operands = ArrayList<android.util.Pair<Double, Double>>()
            if (titleScore > -1) operands.add(android.util.Pair<Double, Double>(titleScore, 1.0))
            if (coverScore > -1) operands.add(android.util.Pair<Double, Double>(coverScore, 1.0))
            if (artistScore > -1) operands.add(android.util.Pair<Double, Double>(artistScore, 0.5))
            return Helper.weigthedAverage(operands)
        }

        fun hash64(): Long {
            return Helper.hash64((reference.id.toString() + "." + duplicate?.id.toString()).toByteArray())
        }
    }

    fun setContent(content: Content) {
        selectedDuplicates.postValue(allDuplicates.value?.filter { it.reference.id == content.id })
    }
}