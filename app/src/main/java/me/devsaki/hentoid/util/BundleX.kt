package me.devsaki.hentoid.util

import android.os.Bundle
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import java.io.Serializable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun Bundle.boolean(default: Boolean) = object : ReadWriteProperty<Any, Boolean> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getBoolean(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) =
        putBoolean(property.name, value)
}

fun Bundle.bundle() = object : ReadWriteProperty<Any, Bundle?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getBundle(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Bundle?) =
        putBundle(property.name, value)
}

fun Bundle.byte(default: Byte) = object : ReadWriteProperty<Any, Byte> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getByte(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Byte) =
        putByte(property.name, value)
}

fun Bundle.char(default: Char) = object : ReadWriteProperty<Any, Char> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getChar(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Char) =
        putChar(property.name, value)
}

fun Bundle.short(default: Short) = object : ReadWriteProperty<Any, Short> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getShort(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Short) =
        putShort(property.name, value)
}

fun Bundle.int(default: Int) = object : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getInt(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) =
        putInt(property.name, value)
}

fun Bundle.long(default: Long) = object : ReadWriteProperty<Any, Long> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getLong(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) =
        putLong(property.name, value)
}

fun Bundle.float(default: Float) = object : ReadWriteProperty<Any, Float> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getFloat(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Float) =
        putFloat(property.name, value)
}

fun Bundle.string(default: String) = object : ReadWriteProperty<Any, String> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getString(property.name, default)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) =
        putString(property.name, value)
}

fun Bundle.size() = object : ReadWriteProperty<Any, Size?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getSize(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Size?) =
        putSize(property.name, value)
}

fun Bundle.sizeF() = object : ReadWriteProperty<Any, SizeF?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getSizeF(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: SizeF?) =
        putSizeF(property.name, value)
}

fun <T : Parcelable> Bundle.parcelable() = object : ReadWriteProperty<Any, T?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getParcelable<T>(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) =
        putParcelable(property.name, value)
}

fun Bundle.serializable() = object : ReadWriteProperty<Any, Serializable?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getSerializable(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Serializable?) =
        putSerializable(property.name, value)
}

fun Bundle.intArray() = object : ReadWriteProperty<Any, IntArray?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getIntArray(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: IntArray?) =
        putIntArray(property.name, value)
}

fun Bundle.intArrayList() = object : ReadWriteProperty<Any, ArrayList<Int>?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        getIntegerArrayList(property.name)

    override fun setValue(thisRef: Any, property: KProperty<*>, value: ArrayList<Int>?) =
        putIntegerArrayList(property.name, value)
}

fun Bundle.boolean() = object : ReadWriteProperty<Any, Boolean?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getBoolean(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean?) {
        if (value == null)
            remove(property.name)
        else
            putBoolean(property.name, value)
    }
}

fun Bundle.byte() = object : ReadWriteProperty<Any, Byte?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getByte(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Byte?) =
        if (value == null)
            remove(property.name)
        else
            putByte(property.name, value)
}

fun Bundle.char() = object : ReadWriteProperty<Any, Char?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getChar(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Char?) =
        if (value == null)
            remove(property.name)
        else
            putChar(property.name, value)
}

fun Bundle.short() = object : ReadWriteProperty<Any, Short?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getShort(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Short?) =
        if (value == null)
            remove(property.name)
        else
            putShort(property.name, value)
}

fun Bundle.int() = object : ReadWriteProperty<Any, Int?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getInt(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int?) =
        if (value == null)
            remove(property.name)
        else
            putInt(property.name, value)
}

fun Bundle.long() = object : ReadWriteProperty<Any, Long?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getLong(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long?) =
        if (value == null)
            remove(property.name)
        else
            putLong(property.name, value)
}

fun Bundle.float() = object : ReadWriteProperty<Any, Float?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getFloat(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Float?) =
        if (value == null)
            remove(property.name)
        else
            putFloat(property.name, value)
}

fun Bundle.string() = object : ReadWriteProperty<Any, String?> {
    override fun getValue(thisRef: Any, property: KProperty<*>) =
        if (containsKey(property.name))
            getString(property.name)
        else
            null

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String?) =
        if (value == null)
            remove(property.name)
        else
            putString(property.name, value)
}