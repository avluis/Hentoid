package me.devsaki.hentoid.json.adapters

import com.squareup.moshi.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal class AndroidPairAdapter(
    private val firstAdapter: JsonAdapter<Any>,
    private val secondAdapter: JsonAdapter<Any>,
    private val listAdapter: JsonAdapter<List<String>>
) : JsonAdapter<androidx.core.util.Pair<Any, Any>>() {

    override fun toJson(writer: JsonWriter, value: androidx.core.util.Pair<Any, Any>?) {
        value ?: throw NullPointerException("value == null")

        writer.beginArray()
        firstAdapter.toJson(writer, value.first)
        secondAdapter.toJson(writer, value.second)
        writer.endArray()
    }

    override fun fromJson(reader: JsonReader): androidx.core.util.Pair<Any, Any>? {
        val list = listAdapter.fromJson(reader) ?: return null

        require(list.size == 2) { "Pair with more or less than two elements: $list" }

        val first = firstAdapter.fromJsonValue(list[0])
            ?: throw IllegalStateException("Pair without first")
        val second = secondAdapter.fromJsonValue(list[1])
            ?: throw IllegalStateException("Pair without second")

        return androidx.core.util.Pair(first, second)
    }
}

class AndroidPairAdapterFactory : JsonAdapter.Factory {

    override fun create(
        type: Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (type !is ParameterizedType || androidx.core.util.Pair::class.java != type.rawType) return null

        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val listAdapter = moshi.adapter<List<String>>(listType)

        return AndroidPairAdapter(
            moshi.adapter(type.actualTypeArguments[0]),
            moshi.adapter(type.actualTypeArguments[1]),
            listAdapter
        )
    }
}