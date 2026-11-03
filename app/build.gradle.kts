import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 服务端地址优先级：命令行 -P > gradle.local.properties（本机调试，已 gitignore）> 这里的线上默认。
// 因为 gradle.local.properties 不是 Gradle 的默认配置文件，不会被自动加载，
// 所以这里的默认值就是发版真实使用的地址，不会出现“本机调试地址被打进发行包”的情况。
val localProps = Properties()
val localPropsFile = rootProject.file("gradle.local.properties")
if (localPropsFile.exists()) localPropsFile.inputStream().use { localProps.load(it) }

fun serverProp(cliKey: String, fallback: String): String =
    (if (project.hasProperty(cliKey)) project.property(cliKey).toString() else null)
        ?: localProps.getProperty(cliKey)
        ?: fallback

val serverApiBaseUrl = serverProp("serverApiBaseUrl", "https://jvkit.cn/music/api/")
val serverOrigin = serverProp("serverOrigin", "https://jvkit.cn/music/")

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

    /**
     * 正式包：开 minify + 资源裁剪，体积从 28MB 砍到一半以下。
     *
     * 签名沿用 debug key——必须和已经发出去的包同签名，否则老用户无法覆盖安装
     * （换 key 会报「应用未安装/签名冲突」）。所以这里只做代码压缩，不换签名。
     */
    buildTypes {
        release {
            // 注意：R8 需要约 4GB 堆。这台 16G 机器构建前先关掉浏览器/微信等再跑，
            // 否则会 Native memory allocation (mmap) failed for G1 virtual space 崩掉。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
