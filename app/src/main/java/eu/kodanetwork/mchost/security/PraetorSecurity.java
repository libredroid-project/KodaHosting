package eu.kodanetwork.mchost.security;

public class PraetorSecurity {
    static {
        System.loadLibrary("embeddedjvm");
    }

    public static native String getSupabaseUrl();
    public static native String getSupabaseKey();
    public static native String getFrpcToken();
    public static native String stringFromJNI();
    public static native void startInotifyWatcher(String dataDir);
}
