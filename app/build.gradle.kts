plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val serverApiBaseUrl = providers.gradleProperty("serverApiBaseUrl").getOrElse("https://jvkit.cn/music/api/")
val serverOrigin = providers.gradleProperty("serverOrigin").getOrElse("https://jvkit.cn/music/")

android {
    namespace = "com.example.helloworld"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.example.helloworld"
        minSdk = 23
        targetSdk = 36
        versionCode = 7
        versionName = "1.7.0"
        buildConfigField("String", "SERVER_API_BASE", "\"$serverApiBaseUrl\"")
        buildConfigField("String", "SERVER_ORIGIN", "\"$serverOrigin\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    // 悬浮球用 LifecycleService 挂 ComposeView（需要 ViewTreeLifecycleOwner）
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-session:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
