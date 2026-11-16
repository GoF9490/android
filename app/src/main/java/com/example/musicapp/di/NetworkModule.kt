package com.example.musicapp.di

import android.content.Context
import com.example.musicapp.BuildConfig
import com.example.musicapp.data.api.MusicApi
import com.example.musicapp.data.api.RetryInterceptor
import com.example.musicapp.data.api.apiJson
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val TIMEOUT_SECONDS = 30L

/** baseUrl의 공백을 제거하고 끝 슬래시를 보장한다(Retrofit 요구 사항). 빈 값은 설정 오류다. */
fun normalizeBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    require(trimmed.isNotEmpty()) { "BASE_URL이 비어 있습니다." }
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

fun createRetrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit {
    return Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(baseUrl))
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

/** 타임아웃만 지정한 인터셉터 없는 기본 클라이언트. callTimeout은 업로드·스트리밍을 위해 두지 않는다. */
fun createBaseOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Named("baseUrl")
    fun provideBaseUrl(): String = normalizeBaseUrl(BuildConfig.BASE_URL)

    @Provides
    @Singleton
    fun provideJson(): Json = apiJson

    /** 인터셉터 없는 공통 원천. 연결 풀·디스패처를 공유한다(Task 014 업로드 클라이언트도 여기서 파생). */
    @Provides
    @Singleton
    @Named("base")
    fun provideBaseOkHttpClient(): OkHttpClient = createBaseOkHttpClient()

    /**
     * API용 클라이언트(수식자 없음). 인터셉터는 이 한 곳에서 순서대로 추가한다.
     * Task 006: AuthInterceptor를 RetryInterceptor보다 먼저 추가한다.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@Named("base") base: OkHttpClient): OkHttpClient {
        return base.newBuilder()
            .addInterceptor(RetryInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        @Named("baseUrl") baseUrl: String,
        client: OkHttpClient,
        json: Json,
    ): Retrofit = createRetrofit(baseUrl, client, json)

    @Provides
    @Singleton
    fun provideMusicApi(retrofit: Retrofit): MusicApi = retrofit.create(MusicApi::class.java)

    /**
     * Coil도 API와 같은 클라이언트 계열을 쓴다.
     * 외부 호스트에 Authorization이 붙지 않는 것은 Task 006의 AuthInterceptor 호스트 검사가 보장한다.
     */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context, client: OkHttpClient): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            }
            .crossfade(true)
            .build()
    }
}
