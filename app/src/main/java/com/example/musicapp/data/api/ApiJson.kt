package com.example.musicapp.data.api

import kotlinx.serialization.json.Json

/** API 전용 단일 Json 인스턴스. 서버 필드 추가와 알 수 없는 enum 값에 견딘다. */
val apiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
