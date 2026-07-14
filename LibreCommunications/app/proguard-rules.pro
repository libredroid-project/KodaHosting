# ============================================================================
# ProGuard / R8 Rules for Libre Communications
# ============================================================================

# ---- Standard Android Keep Rules ----

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enum members
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# ---- Signal Protocol (libsignal-client) ----

-keep class org.signal.libsignal.** { *; }
-keep class org.signal.libsignal.internal.** { *; }
-dontwarn org.signal.libsignal.**

# ---- Room Database ----

-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ---- Gson ----

# Gson uses generic type information stored in a class file when working with fields.
# R8 removes such information by default, so configure it to keep.
-keepattributes Signature

# Gson specific classes
-dontwarn sun.misc.**

# Keep classes that Gson might serialize/deserialize
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# Prevent R8 from removing fields that Gson accesses via reflection
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data model classes used with Gson (adjust package as needed)
-keep class org.librecommunications.app.data.model.** { *; }
-keep class org.librecommunications.app.network.model.** { *; }

# ---- WebRTC ----

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keepclassmembers class org.webrtc.** {
    native <methods>;
}

# ---- SQLCipher ----

-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ---- ZXing (QR Code) ----

-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.barcodescanner.**

# ---- Guardian Project Panic Kit ----

-keep class info.guardianproject.panic.** { *; }
-dontwarn info.guardianproject.panic.**

# ---- AndroidX Navigation ----

-keep class * extends androidx.navigation.Navigator { *; }

# ---- AndroidX Biometric ----

-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ---- AndroidX Security Crypto ----

-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ---- AndroidX Lifecycle ----

-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LiveData { *; }
-dontwarn androidx.lifecycle.**

# ---- View Binding Generated Classes ----

-keep class org.librecommunications.app.databinding.** { *; }

# ---- Application Classes ----

# Keep the Application class
-keep class org.librecommunications.app.LibreCommunicationsApplication { *; }

# Keep all Activities, Services, Receivers, and Providers
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# ---- Optimization ----

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
