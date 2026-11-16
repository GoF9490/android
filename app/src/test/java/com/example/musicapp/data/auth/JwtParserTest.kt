package com.example.musicapp.data.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 테스트용 가짜 토큰만 사용한다(실제 JWT·시크릿 금지)
internal fun fakeJwt(payloadJson: String, padding: Boolean = false): String {
    val encoder = if (padding) Base64.getUrlEncoder() else Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
    val payload = encoder.encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
    return "$header.$payload.sig"
}

class JwtParserTest {

    private val now = 1_000_000L

    @Test
    fun 미래_exp는_만료가_아니다() {
        val token = fakeJwt("""{"sub":"1","exp":${now + 100}}""")
        assertEquals(now + 100, JwtParser.decodeExpiresAt(token))
        assertFalse(JwtParser.isExpired(token, now))
    }

    @Test
    fun 과거_exp는_만료다() {
        assertTrue(JwtParser.isExpired(fakeJwt("""{"exp":${now - 1}}"""), now))
    }

    @Test
    fun 경계값은_exp가_now와_같으면_만료다() {
        assertTrue(JwtParser.isExpired(fakeJwt("""{"exp":$now}"""), now))
        assertFalse(JwtParser.isExpired(fakeJwt("""{"exp":${now + 1}}"""), now))
    }

    @Test
    fun 패딩이_있어도_없어도_디코드한다() {
        val json = """{"exp":${now + 5}}"""
        assertEquals(now + 5, JwtParser.decodeExpiresAt(fakeJwt(json, padding = true)))
        assertEquals(now + 5, JwtParser.decodeExpiresAt(fakeJwt(json, padding = false)))
    }

    @Test
    fun 한글_payload도_디코드한다() {
        val token = fakeJwt("""{"name":"홍길동~?>","exp":${now + 5}}""")
        assertEquals(now + 5, JwtParser.decodeExpiresAt(token))
    }

    @Test
    fun 소수_exp도_처리한다() {
        assertEquals(1_700_000_000L, JwtParser.decodeExpiresAt(fakeJwt("""{"exp":1.7e9}""")))
        assertEquals(now + 5, JwtParser.decodeExpiresAt(fakeJwt("""{"exp":${now + 5}.0}""")))
    }

    @Test
    fun 깨진_토큰은_만료로_취급한다() {
        val broken = listOf(
            "",
            "abc",
            "a.b",
            "a.b.c.d",
            "a.!!!.c",
            fakeJwt("not json"),
            fakeJwt("[1,2]"),
            fakeJwt("""{"exp":"123"}"""),
            fakeJwt("""{"exp":null}"""),
        )
        broken.forEach { token ->
            assertNull(JwtParser.decodeExpiresAt(token))
            assertTrue(JwtParser.isExpired(token, now))
        }
    }

    @Test
    fun exp가_없으면_만료로_취급한다() {
        val token = fakeJwt("""{"sub":"1"}""")
        assertNull(JwtParser.decodeExpiresAt(token))
        assertTrue(JwtParser.isExpired(token, now))
    }
}
