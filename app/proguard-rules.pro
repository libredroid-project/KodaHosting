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
-flattenpackagehierarchy ''
-dontusemixedcaseclassnames

# Strip source file names and line numbers to make stacktraces useless for reverse engineers
-renamesourcefileattribute SourceFile
-keepattributes !SourceFile,!LineNumberTable

# Remove Android logging completely from release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove System.out and System.err logging
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Disable debugging information
-keepattributes !LocalVariableTable
-keepattributes !LocalVariableTypeTable
