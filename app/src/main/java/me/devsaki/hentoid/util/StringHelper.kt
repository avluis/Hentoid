package me.devsaki.hentoid.util

import android.util.Base64
import org.apache.commons.text.StringEscapeUtils
import java.util.Locale
import java.util.regex.Pattern
import java.util.stream.Stream
import kotlin.math.min

private val NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?")
private val STRING_CLEANUP_INVALID_CHARS_PATTERN =
    Pattern.compile("[(\\[\\-+?!_~/,:;|.#\"'’=&)\\]]")


/**
 * Return the given string formatted with a capital letter as its first letter
 *
 * @param s String to format
 * @return Given string formatted with a capital letter as its first letter
 */
fun capitalizeString(s: String?): String {
    return if (s.isNullOrEmpty()) ""
    else if (s.length == 1) s.uppercase(Locale.getDefault())
    else s.substring(0, 1).uppercase(Locale.getDefault()) + s.lowercase(Locale.getDefault())
        .substring(1)
}

/**
 * Transform the given int to format with a given length
 * - If the given length is shorter than the actual length of the string, it will be truncated
 * - If the given length is longer than the actual length of the string, it will be left-padded with the character 0
 *
 * @param value  String to transform
 * @param length Target length of the final string
 * @return Reprocessed string of given length, according to rules documented in the method description
 */
fun formatIntAsStr(value: Int, length: Int): String {
    var result = value.toString()

    if (result.length > length) {
        result = result.substring(0, length)
    } else if (result.length < length) {
        result = String.format("%1$" + length + "s", result).replace(' ', '0')
    }

    return result
}

/**
 * Indicate of the given string is present as a word inside the given expression
 * "present as a word" means present as a substring separated from other substrings by separating characters
 *
 * @param toDetect   String whose presence to detect within the given expression
 * @param expression Expression where the given string will be searched for
 * @return True if the given string is present as a word inside the given expression; false if not
 */
fun isPresentAsWord(toDetect: String, expression: String): Boolean {
    val words = expression.split("\\W".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return Stream.of(*words).anyMatch { w: String ->
        w.equals(
            toDetect,
            ignoreCase = true
        )
    }
}

/**
 * Determine whether the given string represents a numeric value or not
 *
 * @param str Value to test
 * @return True if the given value is numeric (including negative and decimal numbers); false if not
 */
fun isNumeric(str: String): Boolean {
    val m = NUMERIC_PATTERN.matcher(str)
    return m.matches()
}

/**
 * Remove all non-printable characters from the given string
 * https://stackoverflow.com/a/18603020/8374722
 *
 * @param s String to cleanup
 * @return Given string stripped from all its non-printable characters
 */
fun removeNonPrintableChars(s: String?): String {
    if (s.isNullOrEmpty()) return ""

    val newString = StringBuilder(s.length)
    var offset = 0
    while (offset < s.length) {
        val codePoint = s.codePointAt(offset)
        offset += Character.charCount(codePoint)

        val type = Character.getType(codePoint)
        val typeB = min(type, Byte.MAX_VALUE * 1).toByte()
        when (typeB) {
            Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.SURROGATE, Character.UNASSIGNED -> {}
            else -> newString.append(Character.toChars(codePoint))
        }
    }
    return newString.toString()
}

/**
 * Unescape all escaped characters from the given string (Java convention)
 *
 * @param s String to be cleaned up
 * @return Given string where all escaped characters have been unescaped
 */
fun replaceEscapedChars(s: String): String {
    return StringEscapeUtils.unescapeJava(s)
}

/**
 * Simplify the given string by
 * - Removing everything between ()'s, {}'s and []'s
 * - Replacing [-+_~/\,:;|.#"'=&!?]'s by a space
 * - Putting all characters lowercase
 * - Replacing HTML-escaped characters by their ASCII equivalent
 * - Trimming
 *
 * @param s String to simplify
 * @return Simplified string
 */
fun simplify(s: String): String {
    var openBracket = false
    val formattedS =
        StringEscapeUtils.unescapeHtml4(s.lowercase(Locale.getDefault()).trim())
    val result = StringBuilder()
    for (c in formattedS) {
        if (c == '(' || c == '[' || c == '{') openBracket = true
        else if (c == ')' || c == ']' || c == '}') openBracket = false
        else if (c == '-' || c == '_' || c == '?' || c == '!' || c == ':' || c == ';' || c == ',' || c == '~' || c == '/' || c == '\\' || c == '|' || c == '.' || c == '+' || c == '#' || c == '\'' || c == '’' || c == '"' || c == '=' || c == '&') result.append(
            ' '
        )
        else if (!openBracket) result.append(c)
    }
    val resStr = result.toString().trim()
    return resStr.ifEmpty {
        STRING_CLEANUP_INVALID_CHARS_PATTERN.matcher(formattedS)
            .replaceAll("")
    }
}

/**
 * Remove all digits (0-9; not punctuation) from the given string
 *
 * @param s String to remove digits from
 * @return Given string sripped from all its digits
 */
fun removeDigits(s: String): String {
    val result = StringBuilder()
    for (c in s) {
        if (!Character.isDigit(c)) result.append(c)
    }
    return result.toString().trim()
}

/**
 * Remove all non-digits (0-9; not punctuation) from the given string
 *
 * @param s String to keep digits from
 * @return Digits from the given string, in their original order
 */
fun keepDigits(s: String): String {
    val result = StringBuilder()
    for (c in s) {
        if (Character.isDigit(c)) result.append(c)
    }
    return result.toString().trim()
}

// TODO doc
fun locateDigits(s: String): List<Triple<Int, Int, Int>> {
    val result: MutableList<Triple<Int, Int, Int>> = ArrayList()
    var inDigit = false
    var startIndex = -1
    for (i in s.indices) {
        val c = s[i]
        if (Character.isDigit(c) && !inDigit) {
            startIndex = i
            inDigit = true
        } else if (!Character.isDigit(c) && inDigit) {
            val value = s.substring(startIndex, i).toInt()
            result.add(Triple(startIndex, i - 1, value))
            inDigit = false
        }
    }
    if (inDigit) {
        val value = s.substring(startIndex).toInt()
        result.add(Triple(startIndex, s.length - 1, value))
    }
    return result
}

/**
 * Remove any multiple spaces from the given string to replace them with a single space
 * NB1 : This methods is a fast alternative to using Regexes to replace \s by ' '
 * NB2 : Spaces are ' ', '\t', '\n', '\f' and '\r'
 *
 * @param str String to clean spaces from
 * @return Given string with cleaned spaces
 */
fun cleanMultipleSpaces(str: String): String {
    var first = true
    val result = StringBuilder()
    for (c in str) {
        if (c == ' ' || c == '\t' || c == '\n' || c == '\u000C' || c == '\r') {
            if (first) {
                result.append(' ')
                first = false
            }
        } else {
            first = true
            result.append(c)
        }
    }
    return result.toString()
}

/**
 * Decode the given base-64-encoded string
 *
 * @param encodedString Base-64 encoded string to decode
 * @return Raw decoded data
 */
fun decode64(encodedString: String?): ByteArray {
    // Pure Java
    // return org.apache.commons.codec.binary.Base64.decodeBase64(encodedString);
    // Android
    return Base64.decode(encodedString, Base64.DEFAULT)
}

/**
 * Encode the given base-64-encoded string
 *
 * @param rawString Raw string to encode
 * @return Encoded string
 */
fun encode64(rawString: String): String {
    return Base64.encodeToString(rawString.toByteArray(), Base64.DEFAULT)
}

/**
 * Indicate if the given string is the transposition of the other given string
 * Here "X is a transposition of Y" means "X contains all words of Y, potentially arranged in another order"
 * e.g. "word1 word2 word3" is a transposition for "word2 word3 word1"
 *
 * @param referenceCleanup  Cleaned-up reference string
 * @param comparisonCleanup Cleaned-up comparison string
 * @return True if comparisonCleanup is a transposition of referenceCleanup; false if not
 */
fun isTransposition(referenceCleanup: String, comparisonCleanup: String): Boolean {
    if (referenceCleanup == comparisonCleanup) return true
    if (referenceCleanup.replace(" ", "") == comparisonCleanup.replace(" ", "")) return true

    val refParts = referenceCleanup.split(" ")
    val compParts = comparisonCleanup.split(" ")

    if (1 == refParts.size && 1 == compParts.size) return false
    if (refParts.size != compParts.size) return false

    for (s in refParts) if (!compParts.contains(s)) return false
    return true
}