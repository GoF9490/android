package com.example.musicapp.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoginLauncherTest {

    private val expected =
        "http://localhost/oauth2/authorization/google?redirect_uri=musicapp%3A%2F%2Foauth2%2Fcallback"

    @Test
    fun 로그인_URL을_만든다() = assertEquals(expected, buildLoginUrl("http://localhost"))

    @Test
    fun 끝_슬래시가_있어도_중복되지_않는다() = assertEquals(expected, buildLoginUrl("http://localhost/"))

    @Test
    fun 이중_인코딩이_없다() = assertFalse(buildLoginUrl("http://localhost").contains("%25"))
}
