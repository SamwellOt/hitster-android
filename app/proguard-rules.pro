# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hitster.mobile.**$$serializer { *; }
-keepclassmembers class com.hitster.mobile.** { *** Companion; }
-keepclasseswithmembers class com.hitster.mobile.** { kotlinx.serialization.KSerializer serializer(...); }
# okhttp
-dontwarn okhttp3.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
