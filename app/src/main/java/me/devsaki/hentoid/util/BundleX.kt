package me.devsaki.hentoid.util

import android.os.Bundle
import android.os.Parcel
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Creates a property delegate that uses the property name as the key to get and set [Bundle]
 * values. The property will return [default] if the key is not present in the [Bundle].
 */
private fun <T> property(
    default: T,
    get: (String, T) -> T,
    put: (String, T) -> Unit
) = object : ReadWriteProperty<Any, T> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        get(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) =
        put(property.name, value)
}

/**
 * Creates a property delegate that uses the property name as the key to get and set [Bundle]
 * values. The property will return null if the key is not present in the [Bundle]
 * and the value is removed from the [Bundle] when the property is set to null.
 */
private fun <T> Bundle.nullableProperty(
    get: (String) -> T,
    put: (String, T) -> Unit
) = object : ReadWriteProperty<Any, T?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name)) get(property.name) else null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) =
        if (value == null) remove(property.name) else put(property.name, value)
}

fun Bundle.boolean(default: Boolean) = property(default, ::getBoolean, ::putBoolean)

fun Bundle.boolean() = nullableProperty(::getBoolean, ::putBoolean)

fun Bundle.byte(default: Byte) = property(default, ::getByte, ::putByte)

fun Bundle.byte() = nullableProperty(::getByte, ::putByte)

fun Bundle.short(default: Short) = property(default, ::getShort, ::putShort)

fun Bundle.short() = nullableProperty(::getShort, ::putShort)

fun Bundle.int(default: Int) = property(default, ::getInt, ::putInt)

fun Bundle.int() = nullableProperty(::getInt, ::putInt)

fun Bundle.long(default: Long) = property(default, ::getLong, ::putLong)

fun Bundle.long() = nullableProperty(::getLong, ::putLong)

fun Bundle.float(default: Float) = property(default, ::getFloat, ::putFloat)

fun Bundle.float() = nullableProperty(::getFloat, ::putFloat)

fun Bundle.char(default: Char) = property(default, ::getChar, ::putChar)

fun Bundle.char() = nullableProperty(::getChar, ::putChar)

fun Bundle.string(default: String) = property(default, ::getString, ::putString)

fun Bundle.string() = nullableProperty(::getString, ::putString)

fun Bundle.sizeI() = nullableProperty(::getSize, ::putSize)

fun Bundle.sizeF() = nullableProperty(::getSizeF, ::putSizeF)

fun Bundle.intArray() = nullableProperty(::getIntArray, ::putIntArray)

fun Bundle.longArray() = nullableProperty(::getLongArray, ::putLongArray)

fun Bundle.intArrayList() = nullableProperty(::getIntegerArrayList, ::putIntegerArrayList)

fun Bundle.bundle() = nullableProperty(::getBundle, ::putBundle)

fun Bundle.toByteArray(): ByteArray {
    val parcel = Parcel.obtain()
    parcel.writeBundle(this)
    val bytes = parcel.marshall()
    parcel.recycle()
    return bytes
}

fun Bundle.fromByteArray(data: ByteArray): Bundle {
    val parcel = Parcel.obtain()
    parcel.unmarshall(data, 0, data.size)
    parcel.setDataPosition(0)
    val bundle = parcel.readBundle(this.classLoader)
    parcel.recycle()
    return bundle ?: Bundle()
}