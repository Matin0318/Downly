# Downly ProGuard / R8 rules (release builds).

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod, RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# BitTorrent (libtorrent4j uses JNI and reflection)
-keep class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**
-keepclasseswithmembernames class * { native <methods>; }

# kotlinx.serialization: settings, the download list and backups are stored as JSON
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# Ktor and OkHttp (optional platform classes they look for at run time)
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Okio
-dontwarn okio.**
