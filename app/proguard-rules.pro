# Keep Gson serialized classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.monitor.app.config.ConfigModel$** { *; }
-keep class com.monitor.app.db.entities.** { *; }
