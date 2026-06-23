# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in tools/proguard/proguard-android-optimize.txt.

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# Compose
-dontwarn androidx.compose.**
