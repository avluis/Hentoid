package me.devsaki.hentoid.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import info.debatty.java.stringsimilarity.Cosine
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity
import io.reactivex.ObservableEmitter
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class DuplicateHelper {

    companion object {
        // Thresholds according to the "sensibility" setting
        private val COVER_THRESHOLDS = doubleArrayOf(0.71, 0.75, 0.8) // @48-bit resolution, according to calibration tests
        private val TEXT_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
        private val TOTAL_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)

        //private val detectedDuplicatesHash = Collections.synchronizedSet(HashSet<Pair<Long, Long>>())
        private val detectedDuplicatesHash = HashSet<Pair<Long, Long>>()

        /**
         * Detect if there are missing cover hashes
         */
        fun indexCovers(
                context: Context,
                dao: CollectionDAO,
                library: List<Content>,
                interrupted: AtomicBoolean,
                emitter: ObservableEmitter<Pair<Int, Float>>) {
            val noCoverHashes = library.filter { 0L == it.cover.imageHash && !it.cover.status.equals(StatusContent.ONLINE) }
            if (noCoverHashes.isNotEmpty()) {
                val hash = ImagePHash(48, 8)
                var elementsKO = 0
                for ((progress, content) in noCoverHashes.withIndex()) {
                    if (interrupted.get()) break
                    try {
                        FileHelper.getInputStream(context, Uri.parse(content.cover.fileUri))
                                .use {
                                    val b = BitmapFactory.decodeStream(it)
                                    content.cover.imageHash = hash.calcPHash(b)
                                }
                    } catch (e: IOException) {
                        content.cover.imageHash = Long.MIN_VALUE
                        elementsKO++
                        Timber.w(e) // Doesn't break the loop
                    } finally {
                        // Update the picture in DB
                        dao.insertImageFile(content.cover)
                        // Update the book JSON
                        if (content.jsonUri.isNotEmpty()) ContentHelper.updateContentJson(context, content)
                        else ContentHelper.createContentJson(context, content)
                    }

                    emitter.onNext(Pair(DuplicateDetectorWorker.STEP_COVER_INDEX, (progress + 1) * 1f / noCoverHashes.size))
                    Timber.i("Calculating hashes : %s / %s", progress + 1, noCoverHashes.size)
                }
                emitter.onNext(Pair(DuplicateDetectorWorker.STEP_COVER_INDEX, 1f))
                emitter.onComplete()
            }
        }
/*
        private fun nbCombinations(librarySize: Int): Int {
            return (librarySize * (librarySize - 1)) / 2
        }

 */

        fun processLibrary(
                duplicatesDao: DuplicatesDAO,
                library: List<Content>,
                useTitle: Boolean,
                useCover: Boolean,
                useArtist: Boolean,
                sameLanguageOnly: Boolean,
                sensitivity: Int,
                interrupted: AtomicBoolean,
                emitter: ObservableEmitter<Pair<Int, Float>>
        ) {
            Helper.assertNonUiThread()
            Timber.i("Entering processing")

            val textComparator = Cosine()
            var globalProgress: Float
            val nbCombinations = (library.size * (library.size - 1)) / 2

            do {
                for (contentRef in library) {
                    if (interrupted.get()) return
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
                        if (detectedDuplicatesHash.contains(Pair(contentRef.id, contentCandidate.id))) continue
                        if (detectedDuplicatesHash.contains(Pair(contentCandidate.id, contentRef.id))) continue

                        if (interrupted.get()) return

                        globalProgress = detectedDuplicatesHash.size * 1f / nbCombinations
                        emitter.onNext(Pair(DuplicateDetectorWorker.STEP_DUPLICATES, globalProgress))

                        // Process current combination of Content
                        var titleScore = -1f
                        var coverScore = -1f
                        var artistScore = -1f

                        // Remove if not same language
                        if (sameLanguageOnly && !containsSameLanguage(contentRef, contentCandidate)) {
                            detectedDuplicatesHash.add(Pair(contentRef.id, contentCandidate.id))
                            continue
                        }

                        if (useCover) {
                            // Don't analyze anything if covers have not been hashed
                            if (0L == contentRef.cover.imageHash || 0L == contentCandidate.cover.imageHash) continue
                            // Give up analysis for unhashable covers
                            if (Long.MIN_VALUE == contentRef.cover.imageHash || Long.MIN_VALUE == contentCandidate.cover.imageHash) {
                                detectedDuplicatesHash.add(Pair(contentRef.id, contentCandidate.id))
                                continue
                            }

                            val preCoverScore = ImagePHash.similarity(contentRef.cover.imageHash, contentCandidate.cover.imageHash)
                            coverScore = if (preCoverScore >= COVER_THRESHOLDS[sensitivity]) preCoverScore else 0f
                        }

                        if (useTitle) titleScore = computeTitleScore(textComparator, referenceTitleDigits, referenceTitle, contentCandidate, sensitivity)

                        if (useArtist) artistScore = computeArtistScore(contentRef, contentCandidate)

                        val duplicateResult = DuplicateEntry(contentRef.id, contentRef.size, contentCandidate.id, titleScore, coverScore, artistScore)
                        if (duplicateResult.calcTotalScore() >= TOTAL_THRESHOLDS[sensitivity]) duplicatesDao.insertEntry(duplicateResult)
                        detectedDuplicatesHash.add(Pair(contentRef.id, contentCandidate.id))
                    }
                }
                globalProgress = detectedDuplicatesHash.size * 1f / nbCombinations
            } while (globalProgress < 1f)

            emitter.onComplete()
            detectedDuplicatesHash.clear()
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
    }
}