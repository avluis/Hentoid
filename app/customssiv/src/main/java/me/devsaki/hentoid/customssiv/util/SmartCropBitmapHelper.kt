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


// % of max page width/height used as corner
private const val CORNER_ZONE = 0.15

// Tolerance to luminance before detecting a border (%)
private const val LUMI_TOLERANCE = 0.30f

// Number of samples to take to validate border detection
private const val SAMPLE_QTTY = 0.10

// Max accepted standard deviation between samples to validate an actual border has been found
private const val SAMPLES_MAX_STDEV = 0.6


internal suspend fun smartCropBitmap(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
    Timber.d("src (${src.width}, ${src.height})")
    val cornerZoneDim = (min(src.width, src.height) * CORNER_ZONE).roundToInt()
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

    val targetStartX = min(bordersTopLeft.x, bordersBottomLeft.x)
    val targetStartY = min(bordersTopLeft.y, bordersTopRight.y)
    val targetEndX = max(src.width - bordersTopRight.x, src.width - bordersBottomRight.x)
    val targetEndY = max(src.height - bordersBottomLeft.y, src.height - bordersBottomRight.y)
    Timber.d("Keeping original")
    if (0 == targetStartX && 0 == targetStartY && src.width == targetEndX && src.height == targetEndY) return@withContext src

    // Crop
    Timber.d("Target crop to ($targetStartX, $targetStartY) / ($targetEndX, $targetEndY)")
    try {
        return@withContext Bitmap.createBitmap(
            src,
            targetStartX,
            targetStartY,
            targetEndX - targetStartX,
            targetEndY - targetStartY
        )
    } finally {
        src.recycle()
    }
}

// start : Page corner coordinates
// end : Inner page coordinates
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
        else 0,
        if (yBorder > -1)
            if (directionY < 0) height - yBorder else yBorder
        else 0
    )
}

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
            val posX = start.x + i * (width / picks) * direction
            if (testedPos.contains(posX)) continue
            testedPos.add(posX)
            newSample = detectX(pixels, posX, start.y, end.y, width)
            if (newSample > -1) samples.add(newSample)
        }
        pickLevel++
    }
    if (samples.size < minSamples) return -1

    val avg = samples.average()
    val variance = samples.map { (it - avg).pow(2) }.average()
    val stdev = sqrt(variance)
    return if (stdev < SAMPLES_MAX_STDEV) avg.roundToInt() else -1
}

private fun detectDichoX(
    pixels: IntArray,
    start: Point,
    end: Point
): Int {
    val samples = ArrayList<Int>()
    val testedPos = HashSet<Int>()
    val width = abs(end.x - start.x) + 1
    val height = abs(end.y - start.y) + 1
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
            if (newSample > -1) samples.add(newSample)
        }
        pickLevel++
    }
    if (samples.size < minSamples) return -1

    val avg = samples.average()
    val variance = samples.map { (it - avg).pow(2) }.average()
    val stdev = sqrt(variance)
    return if (stdev < SAMPLES_MAX_STDEV) avg.roundToInt() else -1
}

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

private fun getPxLumi(pixels: IntArray, x: Int, y: Int, width: Int): Float {
    return Color.luminance(pixels[x + (y * width)])
}