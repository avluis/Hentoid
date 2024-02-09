package me.devsaki.hentoid.util.network

import android.content.Context
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.util.JsonHelper
import me.devsaki.hentoid.util.file.FileHelper
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.EnumMap

/**
 * Manager class for Oauth2.0 authentication flow
 */
object OauthSessionManager {
    private val activeSessions: MutableMap<Site, OauthSession> =
        EnumMap(me.devsaki.hentoid.enums.Site::class.java)

    fun addSession(site: Site): OauthSession {
        val session = OauthSession(site.name)
        activeSessions[site] = session
        return session
    }

    fun getSessionByState(state: String): OauthSession? {
        for (session in activeSessions.values) {
            if (session.state == state) return session
        }
        return null
    }

    fun getSessionBySite(site: Site): OauthSession? {
        return activeSessions[site]
    }

    private fun getSessionFile(context: Context, host: String): File {
        val dir = context.filesDir
        return File(dir, "$host.json")
    }

    /**
     * Save the Oauth session to the app's internal storage
     *
     * @param context Context to be used
     * @param session Session to be saved
     */
    fun saveSession(context: Context, session: OauthSession) {
        val file = getSessionFile(context, session.host)
        try {
            FileHelper.getOutputStream(file).use { output ->
                JsonHelper.updateJson(
                    session,
                    OauthSession::class.java, output
                )
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    /**
     * Get the Oauth session from the app's internal storage
     *
     * @param context Context to be used
     * @param host    Host the session belongs to
     * @return Oauth session from the given host; null if no such session exists
     */
    fun loadSession(context: Context, host: String): OauthSession? {
        val file = getSessionFile(context, host)
        if (!file.exists()) return null
        try {
            val json = FileHelper.readStreamAsString(file.inputStream())
            return JsonHelper.jsonToObject(json, OauthSession::class.java)
        } catch (e: IOException) {
            Timber.e(e)
        }
        return null
    }

    class OauthSession internal constructor(val host: String) {
        var redirectUri = ""
        var clientId = ""
        var state = ""
        var accessToken = ""
        var refreshToken = ""
        var expiry: Instant? = null
        var targetUrl = ""
        var userName = ""
    }
}