# --- App Specific Logic (The Sledgehammer Rule) ---
# This keeps ALL your code identical to the emulator version.
-keep class com.kisanbandhu.app.** { *; }

# --- GSON & Serialization ---
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- TensorFlow Lite & ML ---
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-keep class org.tensorflow.lite.NativeInterpreterWrapper { *; }
-keep class org.tensorflow.lite.Interpreter { *; }
-keep class org.tensorflow.lite.Tensor { *; }

# --- Retrofit & OkHttp ---
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Firebase & Google Services ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes SourceFile, LineNumberTable

# --- CameraX ---
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }
-dontwarn androidx.camera.core.**
-dontwarn androidx.camera.camera2.**

# --- Coil (Image Loading) ---
-keep class coil.** { *; }
-dontwarn coil.**
