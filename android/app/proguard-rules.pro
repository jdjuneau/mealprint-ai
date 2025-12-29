# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# SLF4J - suppress warnings about missing implementation classes
# This is a logging facade that doesn't need an implementation at compile time
# Android uses its own logging, so these SLF4J implementation classes are not needed
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.helpers.**
-dontwarn org.slf4j.spi.**

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# CRITICAL: Keep Google Sign-In classes (required for Google Fit connection)
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.fitness.** { *; }
-keepclassmembers class com.google.android.gms.auth.api.signin.GoogleSignInAccount {
    *;
}
-keepclassmembers class com.google.android.gms.fitness.FitnessOptions {
    *;
}

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep data classes for serialization
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }

# Keep Health Connect classes
-keep class androidx.health.connect.** { *; }

# CRITICAL: Keep HealthLog sealed class and all subclasses
# These are used extensively throughout the app and must not be obfuscated
-keep class com.coachie.app.data.model.HealthLog { *; }
-keep class com.coachie.app.data.model.HealthLog$* { *; }
-keepclassmembers class com.coachie.app.data.model.HealthLog$* {
    <fields>;
    <methods>;
}

# Keep HealthLog companion objects (contain TYPE constants)
-keepclassmembers class com.coachie.app.data.model.HealthLog$*$Companion {
    *;
}

# Keep GoogleFitSyncService and related health sync services
-keep class com.coachie.app.service.HealthSyncService { *; }
-keep class com.coachie.app.service.GoogleFitSyncService { *; }
-keep class com.coachie.app.data.health.GoogleFitService { *; }
-keep class com.coachie.app.data.health.HealthConnectService { *; }

# Keep all data model classes used for Firestore serialization
-keep class com.coachie.app.data.model.** { *; }
-keepclassmembers class com.coachie.app.data.model.** {
    <fields>;
    <methods>;
}

# Keep classes with @PropertyName annotations (Firestore serialization)
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}
