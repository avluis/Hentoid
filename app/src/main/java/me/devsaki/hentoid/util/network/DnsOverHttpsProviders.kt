package me.devsaki.hentoid.util.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.net.UnknownHostException

// To sync with pref_browser_dns_* in array_preferences.xml
enum class Source(val value: Int) {
    NONE(-1),
    CLOUDFLARE(0),
    QUAD9(1),
    ADGUARD(2);

    companion object {
        fun fromValue(data: Int): Source {
            return entries.firstOrNull { e -> data == e.value } ?: NONE
        }
    }

    fun getPrimaryUrl(): HttpUrl {
        return when (this) {
            CLOUDFLARE -> "https://cloudflare-dns.com/dns-query".toHttpUrl()
            QUAD9 -> "https://dns.quad9.net/dns-query".toHttpUrl()
            ADGUARD -> "https://dns.adguard.com/dns-query".toHttpUrl()
            else -> "".toHttpUrl()
        }
    }

    fun getHosts(): List<InetAddress> {
        return when (this) {
            CLOUDFLARE -> CLOUDFARE_HOSTS.mapNotNull { s -> silentGetByName(s) }
            QUAD9 -> QUAD9_HOSTS.mapNotNull { s -> silentGetByName(s) }
            ADGUARD -> ADGUARD_HOSTS.mapNotNull { s -> silentGetByName(s) }
            else -> emptyList()
        }
    }
}

private val CLOUDFARE_HOSTS = arrayOf(
    "162.159.36.1",
    "162.159.46.1",
    "1.1.1.1",
    "1.0.0.1",
    "162.159.132.53",
    "2606:4700:4700::1111",
    "2606:4700:4700::1001",
    "2606:4700:4700::0064",
    "2606:4700:4700::6400"
)
private val QUAD9_HOSTS = arrayOf("9.9.9.9", "149.112.112.112")
private val ADGUARD_HOSTS = arrayOf("94.140.14.14", "94.140.15.15")

fun silentGetByName(host: String): InetAddress? {
    return try {
        InetAddress.getByName(host)
    } catch (e: UnknownHostException) {
        null
    }
}