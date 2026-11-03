# ===== Gson（数据类字段靠反射，必须保留） =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keep class com.example.helloworld.data.remote.** { *; }
-keep class com.example.helloworld.data.local.** { *; }
-keep class com.example.helloworld.bubble.BubbleRingItems { *; }

# Gson 的泛型 TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ===== Retrofit =====
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ===== Media3 / ExoPlayer =====
-dontwarn androidx.media3.**

# ===== Compose =====
-dontwarn androidx.compose.**

# ===== 前台服务 / 广播（类名要能在 Manifest 里解析） =====
-keep class com.example.helloworld.recognize.SystemCaptureService { *; }
-keep class com.example.helloworld.bubble.BubbleService { *; }
-keep class com.example.helloworld.playback.YinHeMediaService { *; }
