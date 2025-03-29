@file:JvmName(name = "Consts")

package me.devsaki.hentoid.core

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

typealias BiConsumer<T, U> = (T, U) -> Unit
typealias Consumer<T> = (T) -> Unit
typealias SuspendRunnable = suspend () -> Unit

fun <T> lazyWithReset(initializer: () -> T): ResetLazy<T> = ResetLazy(initializer)

class ResetLazy<T>(private val initializer: () -> T) {
    private val lazy: AtomicReference<Lazy<T>> = AtomicReference(lazy(initializer))
    operator fun getValue(ref: Any?, property: KProperty<*>): T = lazy.get().getValue(ref, property)
    fun reset(): Unit = lazy.set(lazy(initializer))
}

const val DEFAULT_PRIMARY_FOLDER_OLD = "Hentoid"
const val DEFAULT_PRIMARY_FOLDER = ".Hentoid"

const val JSON_FILE_NAME_OLD = "data.json"
const val JSON_FILE_NAME = "content.json"
const val JSON_FILE_NAME_V2 = "contentV2.json"
const val JSON_ARCHIVE_SUFFIX = "_h"

const val QUEUE_JSON_FILE_NAME = "queue.json"
const val BOOKMARKS_JSON_FILE_NAME = "bookmarks.json"
const val GROUPS_JSON_FILE_NAME = "groups.json"
const val RENAMING_RULES_JSON_FILE_NAME = "rules.json"

const val THUMB_FILE_NAME = "thumb"
const val EXT_THUMB_FILE_PREFIX = "ext-thumb-"
const val UGOIRA_CACHE_FOLDER = "ugoira"

const val SEED_CONTENT = "content"
const val SEED_PAGES = "pages"

const val WORK_CLOSEABLE = "closeable"

const val CLOUDFLARE_COOKIE = "cf_clearance"


const val URL_GITHUB = "https://github.com/AVnetWS/Hentoid"
const val URL_GITHUB_WIKI = "https://github.com/AVnetWS/Hentoid/wiki"
const val URL_GITHUB_WIKI_TRANSFER =
    "https://github.com/avluis/Hentoid/wiki/Transferring-your-collection-between-devices"
const val URL_GITHUB_WIKI_STORAGE = "https://github.com/avluis/Hentoid/wiki/Storage-management"
const val URL_DISCORD = "https://discord.gg/TNCwwUw"