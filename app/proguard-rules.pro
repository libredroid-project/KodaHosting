-keepattributes Signature
-keepattributes *Annotation*

# Keep Retrofit and GSON
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Explicitly keep JNI classes
-keep class eu.kodanetwork.mchost.security.PraetorSecurity { *; }
-keep class eu.kodanetwork.mchost.service.IsolatedJvmService { *; }

# Aggressive obfuscation
-repackageclasses ''
-allowaccessmodification
