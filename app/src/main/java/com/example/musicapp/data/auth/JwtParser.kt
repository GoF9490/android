package com.example.musicapp.data.auth

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * JWT payload의 exp(초)를 서명 검증 없이 디코드한다(표시·조기 로그아웃 판단용, 최종 기준은 서버).
 * 해석할 수 없는 토큰은 만료로 취급한다. 예외를 던지지 않으며 토큰 조각을 메시지에 담지 않는다.
 */
object JwtParser {

    /** exp(초 단위 epoch). 형식이 깨졌거나 exp가 숫자가 아니면 null. */
    fun decodeExpiresAt(token: String): Long? {
        return try {
            val parts = token.split('.')
            if (parts.size != 3) return null
            val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            val obj = Json.parseToJsonElement(payload) as? JsonObject ?: return null
            val exp = obj["exp"] as? JsonPrimitive ?: return null
            if (exp.isString) return null
            exp.doubleOrNull?.toLong()
        } catch (e: Exception) {
            null
        }
    }

    /** exp가 now 이하이거나 해석 불가(exp 없음 포함)면 만료. */
    fun isExpired(token: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val exp = decodeExpiresAt(token) ?: return true
        return exp <= nowSeconds
    }
}
