package me.devsaki.hentoid.json.oauth2

import com.squareup.moshi.Json

data class Oauth2AccessToken(
    @Json(name = "access_token")
    val accessToken: String,
    @Json(name = "token_type")
    val tokenType: String,
    @Json(name = "expires_in")
    val expiresIn: String,
    @Json(name = "refresh_token")
    val refreshToken: String,
)