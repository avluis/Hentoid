package me.devsaki.hentoid.util.file

import java.math.BigInteger

// Original code from org.apache.commons.io.FileUtils

/**
 * The number of bytes in a kilobyte.
 */
const val ONE_KB: Long = 1024

/**
 * The number of bytes in a kilobyte.
 */
val ONE_KB_BI = BigInteger.valueOf(ONE_KB)

/**
 * The number of bytes in a megabyte.
 */
const val ONE_MB = ONE_KB * ONE_KB

/**
 * The number of bytes in a megabyte.
 */
val ONE_MB_BI = ONE_KB_BI.multiply(ONE_KB_BI)

/**
 * The number of bytes in a gigabyte.
 */
const val ONE_GB = ONE_KB * ONE_MB

/**
 * The number of bytes in a gigabyte.
 */
val ONE_GB_BI = ONE_KB_BI.multiply(ONE_MB_BI)

/**
 * The number of bytes in a terabyte.
 */
const val ONE_TB = ONE_KB * ONE_GB

/**
 * The number of bytes in a terabyte.
 */
val ONE_TB_BI = ONE_KB_BI.multiply(ONE_GB_BI)

/**
 * The number of bytes in a petabyte.
 */
const val ONE_PB = ONE_KB * ONE_TB

/**
 * The number of bytes in a petabyte.
 */
val ONE_PB_BI = ONE_KB_BI.multiply(ONE_TB_BI)

/**
 * The number of bytes in an exabyte.
 */
const val ONE_EB = ONE_KB * ONE_PB

/**
 * The number of bytes in an exabyte.
 */
val ONE_EB_BI = ONE_KB_BI.multiply(ONE_PB_BI)

/**
 * The number of bytes in a zettabyte.
 */
val ONE_ZB = BigInteger.valueOf(ONE_KB).multiply(BigInteger.valueOf(ONE_EB))

/**
 * The number of bytes in a yottabyte.
 */
val ONE_YB = ONE_KB_BI.multiply(ONE_ZB)