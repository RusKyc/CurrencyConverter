# kotlinx.serialization, Retrofit, OkHttp, Room and Hilt ship their own consumer rules.
# Keep DTOs explicitly so JSON mapping survives aggressive shrinking.
-keep class com.currencyconverter.app.data.remote.dto.** { *; }
-keepclassmembers class com.currencyconverter.app.data.remote.dto.** {
    *** Companion;
}
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
