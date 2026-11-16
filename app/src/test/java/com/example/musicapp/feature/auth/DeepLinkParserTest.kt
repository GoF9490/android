package com.example.musicapp.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeepLinkParserTest {

    private fun parse(
        token: String? = null,
        error: String? = null,
        scheme: String = "musicapp",
        host: String = "oauth2",
        path: String = "/callback",
    ) = parseDeepLink(scheme, host, path, token, error)

    @Test
    fun token이_있으면_Token() = assertEquals(DeepLinkResult.Token("abc"), parse(token = "abc"))

    @Test
    fun error가_있으면_Error() =
        assertEquals(DeepLinkResult.Error("access_denied"), parse(error = "access_denied"))

    @Test
    fun error가_빈값이면_코드_null() = assertEquals(DeepLinkResult.Error(null), parse(error = ""))

    @Test
    fun 둘_다_없으면_None() = assertEquals(DeepLinkResult.None, parse())

    @Test
    fun token과_error가_함께_있으면_token_우선() =
        assertEquals(DeepLinkResult.Token("abc"), parse(token = "abc", error = "x"))

    @Test
    fun token이_빈값이면_error를_사용() =
        assertEquals(DeepLinkResult.Error("x"), parse(token = "", error = "x"))

    @Test
    fun 콜백이_아니면_None() {
        assertEquals(DeepLinkResult.None, parse(token = "abc", scheme = "https"))
        assertEquals(DeepLinkResult.None, parse(token = "abc", host = "other"))
        assertEquals(DeepLinkResult.None, parse(token = "abc", path = "/other"))
    }

    @Test
    fun Token_toString은_토큰을_노출하지_않는다() {
        assertFalse(DeepLinkResult.Token("secret-jwt").toString().contains("secret-jwt"))
    }
}
