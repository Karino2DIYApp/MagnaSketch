# Onyx SDK
-keep class com.onyx.** { *; }
-keep interface com.onyx.** { *; }
-keep enum com.onyx.** { *; }
-keepclassmembers class com.onyx.** { *; }
-dontwarn com.onyx.android.sdk.**

# Fix for appComponentFactory
-keep class androidx.core.app.CoreComponentFactory { *; }
