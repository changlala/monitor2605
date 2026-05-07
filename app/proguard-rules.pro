# Keep Gson serialized classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.monitor.app.config.ConfigModel$** { *; }
-keep class com.monitor.app.db.entities.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
