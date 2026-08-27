package eu.kodanetwork.mchost.security;

/*
 * Copyright (c) 2026 Karol Brzostowski / KodaHosting
 *
 * Triple-Licensed under:
 *   - GNU General Public License v3 (GPL-3.0) — see LICENSE
 *   - Libre Open Project License v1.0 PREVIEW — see LOPL_v1.0_PREVIEW.md
 *   - Commercial License — see COMMERCIAL-LICENSE.md
 *
 * For commercial inquiries: licence@kodaserv.eu
 */


public class PraetorSecurity {
    static {
        System.loadLibrary("embeddedjvm");
    }

    public static native String getSupabaseUrl();
    public static native String getSupabaseKey();
    public static native String getFrpcToken();
    public static native String getBoreHost();
    public static native String stringFromJNI();
    public static native void startInotifyWatcher(String[] filesToWatch);
}
