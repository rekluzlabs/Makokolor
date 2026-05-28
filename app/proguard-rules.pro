# ProGuard / R8 Rules for Photo Restoration App
# Minimize APK size while keeping ONNX Runtime functional

# ============================================================================
# CRITICAL: ONNX Runtime (DO NOT OBFUSCATE)
# ============================================================================
-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep native methods for ONNX
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# Jetpack Compose (Minimal keep rules)
# ============================================================================
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ============================================================================
# Android Lifecycle & Core Libraries
# ============================================================================
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.core.app.ActivityCompat { *; }

# ============================================================================
# Coil Image Library
# ============================================================================
-keep class coil.** { *; }
-keep interface coil.** { *; }

# ============================================================================
# Kotlin Coroutines
# ============================================================================
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# ============================================================================
# Custom App Classes (Photography Engine)
# ============================================================================
-keep class com.rekluzlabs.makokolor.engine.** { *; }
-keep class com.rekluzlabs.makokolor.ui.** { *; }
-keepclassmembers class com.rekluzlabs.makokolor.** { *; }

# ============================================================================
# Remove unused code
# ============================================================================
-dontshrink  # For now; can enable after testing
-dontoptimize  # Keep for safety with ONNX

# Remove unused attributes
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature

# ============================================================================
# Aggressive optimization (safe options)
# ============================================================================
-repackageclasses
-allowaccessmodification
-mergeinterfacesaggressively

# ============================================================================
# Remove logging (reduces size)
# ============================================================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================================================
# Android framework
# ============================================================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# ============================================================================
# Remove unused Apache Commons if included
# ============================================================================
-dontwarn org.apache.commons.**
-dontwarn org.apache.**

# ============================================================================
# Remove unused Google services warnings
# ============================================================================
-dontwarn com.google.**
-keep class com.google.android.gms.** { *; }

# ============================================================================
# Enum optimization
# ============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# Serialization (if used)
# ============================================================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================================
# Parceable (Android serialization)
# ============================================================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================================================
# R classes (Android resources)
# ============================================================================
-keepclassmembers class **.R$* {
    public static <fields>;
}
