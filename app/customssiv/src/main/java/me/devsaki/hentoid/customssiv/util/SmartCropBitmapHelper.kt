package me.devsaki.hentoid.customssiv.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt


// Default % of max page width/height used as corner
private const val CORNER_ZONE_DEFAULT = 0.15

// Tolerance to luminance before detecting a border (%)
private const val LUMI_TOLERANCE = 0.30f

// Number of samples to take to validate border detection
private const val SAMPLE_QTTY = 0.10

// Max accepted standard deviation between samples
private const val SAMPLES_STDEV_THRESHOLD = 1
private const val SAMPLES_STDEV_FACTOR_THRESHOLD = 2

internal suspend fun smartCropBitmapDims(
    src: Bitmap
): Pair<Point, Point> {
    return smartCropBitmap(src, true).second
}

internal suspend fun smartCropBitmap(
    src: Bitmap
): Bitmap {
    return smartCropBitmap(src, false).first
}

private suspend fun smartCropBitmap(
    src: Bitmap,
    onlyDimensions: Boolean,
    cornerZone: Double = CORNER_ZONE_DEFAULT
): Pair<Bitmap, Pair<Point, Point>> =
    withContext(Dispatchers.Default) {
        Timber.d("src (${src.width}, ${src.height})")
        val cornerZoneDim = (min(src.width, src.height) * cornerZone).roundToInt()
        val bmpCorner = IntArray(cornerZoneDim * cornerZoneDim)
        Timber.d("cornerZone $cornerZoneDim")

        // Top left corner
        src.getPixels(bmpCorner, 0, cornerZoneDim, 0, 0, cornerZoneDim, cornerZoneDim)

        val bordersTopLeft =
            detectBorders(bmpCorner, Point(0, 0), Point(cornerZoneDim - 1, cornerZoneDim - 1))
        Timber.d("top left $bordersTopLeft")

        // Top right corner
        src.getPixels(
            bmpCorner,
            0,
            cornerZoneDim,
            src.width - cornerZoneDim,
            0,
            cornerZoneDim,
            cornerZoneDim
        )

        val bordersTopRight =
            detectBorders(bmpCorner, Point(cornerZoneDim - 1, 0), Point(0, cornerZoneDim - 1))
        Timber.d("top right $bordersTopRight")

        // Bottom left corner
        src.getPixels(
            bmpCorner,
            0,
            cornerZoneDim,
            0,
            src.height - cornerZoneDim,
            cornerZoneDim,
            cornerZoneDim
        )

        val bordersBottomLeft =
            detectBorders(bmpCorner, Point(0, cornerZoneDim - 1), Point(cornerZoneDim - 1, 0))
        Timber.d("bottom left $bordersBottomLeft")

        // Bottom right corner
        src.getPixels(
            bmpCorner,
            0,
            cornerZoneDim,
            src.width - cornerZoneDim,
            src.height - cornerZoneDim,
            cornerZoneDim,
            cornerZoneDim
        )

        val bordersBottomRight =
            detectBorders(bmpCorner, Point(cornerZoneDim - 1, cornerZoneDim - 1), Point(0, 0))
        Timber.d("bottom right $bordersBottomRight")

        // Retry with a double corner surface if detection failed somewhere during 1st try
        if (cornerZone == CORNER_ZONE_DEFAULT && (
                    bordersTopLeft.x < 0 || bordersTopLeft.y < 0 || bordersBottomLeft.x < 0 || bordersBottomLeft.y < 0
                            || bordersTopRight.x < 0 || bordersTopRight.y < 0 || bordersBottomRight.x < 0 || bordersBottomRight.y < 0
                    )
        ) {
            Timber.d("Retrying with larger corners")
            return@withContext smartCropBitmap(src, onlyDimensions, cornerZone * 2)
        }

        val targetStartX = computeStartCoordinates(bordersTopLeft.x, bordersBottomLeft.x)
        val targetStartY = computeStartCoordinates(bordersTopLeft.y, bordersTopRight.y)
        val targetEndX = computeEndCoordinates(bordersTopRight.x, bordersBottomRight.x, src.width)
        val targetEndY =
            computeEndCoordinates(bordersBottomLeft.y, bordersBottomRight.y, src.height)
        val target = Pair(Point(targetStartX, targetStartY), Point(targetEndX, targetEndY))

        if (0 == targetStartX && 0 == targetStartY && src.width == targetEndX && src.height == targetEndY) {
            Timber.d("Keeping original")
            return@withContext Pair(src, target)
        }

        if (onlyDimensions) return@withContext Pair(src, target)

        // Crop
        Timber.d("Target crop to ($targetStartX, $targetStartY) / ($targetEndX, $targetEndY)")
        try {
            return@withContext Pair(
                Bitmap.createBitmap(
                    src,
                    targetStartX,
                    targetStartY,
                    targetEndX - targetStartX,
                    targetEndY - targetStartY
                ), target
            )
        } finally {
            src.recycle()
        }
    }

private fun computeStartCoordinates(c1: Int, c2: Int): Int {
    // Nothing found at all
    if (c1 < 0 && c2 < 0) return 0
    // One corner found something; the other didn't
    if (c1 < 0) return c2
    if (c2 < 0) return c1
    // Found something on both corners
    return min(c1, c2)
}

private fun computeEndCoordinates(c1: Int, c2: Int, referenceSide: Int): Int {
    // Nothing found at all
    if (c1 < 0 && c2 < 0) return referenceSide
    // One corner found something; the other didn't
    if (c1 < 0) return referenceSide - c2
    if (c2 < 0) return referenceSide - c1
    // Found something on both corners
    return max(referenceSide - c1, referenceSide - c2)
}

/**
 * Detect borders
 *
 * @param pixels    Array of pixels to analyze
 * @param start     Page corner coordinates
 * @param end       Inner page coordinates
 * @return          -1 if detection fails (no samples; e.g. whole corner is solid)
 */
private fun detectBorders(pixels: IntArray, start: Point, end: Point): Point {
    val width = abs(end.x - start.x) + 1
    val height = abs(end.y - start.y) + 1
    val directionX = if (start.x < end.x) 1 else -1
    val directionY = if (start.y < end.y) 1 else -1

    val yBorder = detectDichoY(pixels, start, end)
    val xBorder = detectDichoX(pixels, start, end)

    return Point(
        if (xBorder > -1)
            if (directionX < 0) width - xBorder else xBorder
        else -1,
        if (yBorder > -1)
            if (directionY < 0) height - yBorder else yBorder
        else -1
    )
}

/**
 * Detect dicho y
 *
 * @param pixels    Array of pixels to analyze
 * @param start     Page corner coordinates
 * @param end       Inner page coordinates
 * @return          -1 if detection fails (no samples; e.g. whole corner is solid)
 */
private fun detectDichoY(
    pixels: IntArray,
    start: Point,
    end: Point
): Int {
    val samples = ArrayList<Int>()
    val testedPos = HashSet<Int>()
    val width = abs(end.x - start.x) + 1
    val direction = if (start.x < end.x) 1 else -1
    val minSamples = width * SAMPLE_QTTY

    // Initial result (end of zone)
    samples.add(detectX(pixels, end.x, start.y, end.y, width))

    var newSample = detectX(pixels, start.x + (width / 2) * direction, start.y, end.y, width)
    if (newSample > -1) samples.add(newSample)

    var pickLevel = 2
    while (samples.size < minSamples && pickLevel < 8) {
        val picks = 2.0.pow(pickLevel).roundToInt()
        for (i in 1..picks) {
            val posX = start.x + i * ((width - 1) / picks) * direction
            if (testedPos.contains(posX)) continue
            testedPos.add(posX)
            newSample = detectX(pixels, posX, start.y, end.y, width)
            if (newSample > -1) {
                Timber.v("sampleY $newSample @ $posX")
                samples.add(newSample)
            }
        }
        pickLevel++
    }
    if (samples.size < minSamples) {
        Timber.d("sizeY KO ${samples.size} / $minSamples")
        return -1
    }

    return computeSamples(samples, direction)
}

/**
 * Detect dicho x
 *
 * @param pixels    Array of pixels to analyze
 * @param start     Page corner coordinates
 * @param end       Inner page coordinates
 * @return          -1 if detection fails (no samples; e.g. whole corner is solid)
 */
private fun detectDichoX(
    pixels: IntArray,
    start: Point,
    end: Point
): Int {
    val samples = ArrayList<Int>()
    val testedPos = HashSet<Int>()
    val width = abs(end.x - start.x) + 1
    val height = abs(end.y - start.y)
    val direction = if (start.y < end.y) 1 else -1
    val minSamples = height * SAMPLE_QTTY

    // Initial result (end of zone)
    samples.add(detectY(pixels, end.y, start.x, end.x, width))

    var newSample = detectY(pixels, start.y + (height / 2) * direction, start.x, end.x, width)
    if (newSample > -1) samples.add(newSample)

    var pickLevel = 2
    while (samples.size < minSamples && pickLevel < 8) {
        val picks = 2.0.pow(pickLevel).roundToInt()
        for (i in 1..picks) {
            val posY = start.y + i * (height / picks) * direction
            if (testedPos.contains(posY)) continue
            testedPos.add(posY)
            newSample = detectY(pixels, posY, start.x, end.x, width)
            if (newSample > -1) {
                Timber.v("sampleX $newSample @ $posY")
                samples.add(newSample)
            }
        }
        pickLevel++
    }
    if (samples.size < minSamples) {
        Timber.d("sizeX KO ${samples.size} / $minSamples")
        return -1
    }

    return computeSamples(samples, direction)
}

private fun computeSamples(samples: Collection<Int>, direction: Int): Int {
    // Remove outliers (> 2 * standard deviation if stdev is significant)
    val avg = samples.average()
    val variance = samples.map { (it - avg).pow(2) }.average()
    val stdev = sqrt(variance)

    val filteredSamples = if (stdev > SAMPLES_STDEV_THRESHOLD)
        samples.filterNot { abs(it - avg) > stdev * SAMPLES_STDEV_FACTOR_THRESHOLD }
    else samples
    return if (direction > 0) filteredSamples.min() else filteredSamples.max()
}

/**
 * Find the Y position with a luminance delta that exceeds LUMI_TOLERANCE for the given X position
 *
 * @param pixels    Pixels array to analyze
 * @param posX      X position to analyze
 * @param startY    Y start position
 * @param endY      Y end position
 * @param width     Width of the pixels array
 * @return Y position with a luminance delta that exceeds LUMI_TOLERANCE for the given X position;
 * -1 if nothing found
 */
private fun detectX(pixels: IntArray, posX: Int, startY: Int, endY: Int, width: Int): Int {
    var initLumi = -1f
    val direction = if (startY < endY) 1 else -1
    var y = startY
    while (y != endY) {
        val lumi = getPxLumi(pixels, posX, y, width)
        if (initLumi < 0) initLumi = lumi
        val delta = abs(lumi - initLumi)
        if (delta > LUMI_TOLERANCE) return y
        y += direction
    }
    return -1
}

/**
 * Find the X position with a luminance delta that exceeds LUMI_TOLERANCE for the given Y position
 *
 * @param pixels    Pixels array to analyze
 * @param posY      Y position to analyze
 * @param startX    X start position
 * @param endX      X end position
 * @param width     Width of the pixels array
 * @return X position with a luminance delta that exceeds LUMI_TOLERANCE for the given Y position;
 * -1 if nothing found
 */
private fun detectY(pixels: IntArray, posY: Int, startX: Int, endX: Int, width: Int): Int {
    var initLumi = -1f
    val direction = if (startX < endX) 1 else -1
    var x = startX
    while (x != endX) {
        val lumi = getPxLumi(pixels, x, posY, width)
        if (initLumi < 0) initLumi = lumi
        val delta = abs(lumi - initLumi)
        if (delta > LUMI_TOLERANCE) return x
        x += direction
    }
    return -1
}

/**
 * Get luminance for the pixel at coords (x,y)
 *
 * @param pixels    Pixels array to analyze
 * @param x         X position where to get the luminance from
 * @param y         Y position where to get the luminance from
 * @param width     Width of the pixels array
 */
private fun getPxLumi(pixels: IntArray, x: Int, y: Int, width: Int): Float {
    val pos = x + (y * width)
    if (pos < 0 || pos >= pixels.size) return 0f
    return Color.luminance(pixels[pos])
}