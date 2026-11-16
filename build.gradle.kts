// 루트 빌드 파일: 플러그인 버전만 선언하고 모듈에서 적용한다
// AGP 9.x는 Kotlin을 내장하므로 org.jetbrains.kotlin.android 플러그인은 사용하지 않는다
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
