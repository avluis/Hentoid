package me.devsaki.hentoid.util

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import me.devsaki.hentoid.enums.AttributeType.AttributeTypeAdapter
import me.devsaki.hentoid.json.adapters.KotlinPairAdapterFactory
import me.devsaki.hentoid.util.file.NameFilter
import me.devsaki.hentoid.util.file.findOrCreateDocumentFile
import me.devsaki.hentoid.util.file.getExtension
import me.devsaki.hentoid.util.file.getOutputStream
import me.devsaki.hentoid.util.file.readFileAsString
import me.devsaki.hentoid.util.file.syncStream
import timber.log.Timber
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Type
import java.util.Date

const val JSON_MIME_TYPE = "application/json"

private val jsonFilter =
    NameFilter { displayName: String ->
        getExtension(displayName).equals(
            "json",
            ignoreCase = true
        )
    }

val LIST_STRINGS: Type = Types.newParameterizedType(
    MutableList::class.java,
    String::class.java
)

val MAP_STRINGS: Type = Types.newParameterizedType(
    MutableMap::class.java,
    String::class.java,
    String::class.java
)

private val MOSHI = Moshi.Builder()
    .add(Date::class.java, Rfc3339DateJsonAdapter())
    .add(AttributeTypeAdapter())
    .add(KotlinPairAdapterFactory())
    .addLast(KotlinJsonAdapterFactory())
    .build()


/**
 * Serialize the given object to JSON format
 *
 * @param o    Object to serialize
 * @param type Type of the output JSON structure to use
 * @param <K>  Type of the given object
 * @return String containing the given object serialized to JSON format
</K> */
fun <K> serializeToJson(o: K, type: Type): String {
    val jsonAdapter = MOSHI.adapter<K>(type)

    return jsonAdapter.toJson(o)
}

/**
 * Serialize and save the object contents to a json file in the given directory.
 * The JSON file is created if it doesn't exist
 *
 * @param context  Context to be used
 * @param obj      Object to be serialized and saved
 * @param type     Type of the output JSON structure to use
 * @param dir      Existing folder to save the JSON file to
 * @param fileName Name of the output file
 * @param <K>      Type of the given object
 * @return DocumentFile where the object has been serialized and saved
 * @throws IOException If anything happens during file I/O
</K> */
@Throws(IOException::class)
fun <K> jsonToFile(
    context: Context,
    obj: K,
    type: Type,
    dir: DocumentFile,
    fileName: String
): DocumentFile {
    val file = findOrCreateDocumentFile(context, dir, JSON_MIME_TYPE, fileName)
        ?: throw IOException("Failed creating file " + fileName + " in " + dir.uri.path)

    getOutputStream(context, file).use { output ->
        if (output != null) updateJson(obj, type, output)
        else Timber.w("JSON file creation failed for %s", file.uri.path)
    }
    return file
}

/**
 * Serialize and save the object contents to the given OutputStream using the JSON format
 *
 * @param obj Object to serialize
 * @param type   Type of the output JSON structure to use
 * @param output OutputStream to write to
 * @param <K>    Type of the given object
 * @throws IOException If anything happens during file I/O
</K> */
@Throws(IOException::class)
fun <K> updateJson(obj: K, type: Type, output: OutputStream) {
    val bytes = serializeToJson(obj, type).toByteArray()
    output.write(bytes)
    if (output is FileOutputStream) syncStream(output)
    output.flush()
}

/**
 * Convert the JSON data contained in the given file to an object of the given type
 *
 * @param context Context to be used
 * @param f       File to read JSON data from
 * @param type    Class of the input JSON structure to use
 * @param <T>     Type of the converted object
 * @return Object of the given type representing the JSON data contained in the given file
 * @throws IOException If anything happens during file I/O
</T> */
@Throws(IOException::class)
fun <T> jsonToObject(context: Context, f: DocumentFile, type: Class<T>): T? {
    return jsonToObject(readFileAsString(context, f), type)
}

/**
 * Convert the JSON data contained in the given file to an object of the given type
 *
 * @param context Context to be used
 * @param f       File to read JSON data from
 * @param type    Type of the input JSON structure to use
 * @param <T>     Type of the converted object
 * @return Object of the given type representing the JSON data contained in the given file
 * @throws IOException If anything happens during file I/O
</T> */
@Throws(IOException::class)
fun <T> jsonToObject(context: Context, f: DocumentFile, type: Type): T? {
    return jsonToObject(readFileAsString(context, f), type)
}

/**
 * Convert JSON data contained in the given string to an object of the given type
 *
 * @param s    JSON data in string format
 * @param type Class of the input JSON structure to use
 * @param <T>  Type of the converted object
 * @return Object of the given type representing the JSON data contained in the given string
 * @throws IOException If anything happens during file I/O
</T> */
@Throws(IOException::class)
fun <T> jsonToObject(s: String, type: Class<T>): T? {
    val jsonAdapter = MOSHI.adapter(type)
    return jsonAdapter.lenient().fromJson(s)
}

/**
 * Convert JSON data contained in the given string to an object of the given type
 *
 * @param s    JSON data in string format
 * @param type Type of the input JSON structure to use
 * @param <T>  Type of the converted object
 * @return Object of the given type representing the JSON data contained in the given string
 * @throws IOException If anything happens during file I/O
</T> */
@Throws(IOException::class)
fun <T> jsonToObject(s: String, type: Type): T? {
    val jsonAdapter = MOSHI.adapter<T>(type)
    return jsonAdapter.lenient().fromJson(s)
}

/**
 * Build a [NameFilter] only accepting json files
 *
 * @return [NameFilter] only accepting json files
 */
fun getJsonNamesFilter(): NameFilter {
    return jsonFilter
}