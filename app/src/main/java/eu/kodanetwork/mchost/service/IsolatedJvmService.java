package eu.kodanetwork.mchost.service;

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


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import eu.kodanetwork.mchost.IJvmService;

public class IsolatedJvmService extends Service {

    private static final String TAG = "IsolatedJvmService";

    private static Throwable initError = null;

    static {
        try {
            System.loadLibrary("c++_shared");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load c++_shared", t);
        }
        try {
            System.loadLibrary("embeddedjvm");
        } catch (Throwable t) {
            initError = t;
            Log.e(TAG, "Failed to load embeddedjvm", t);
        }
    }

    public static native int startEmbeddedJvmNative(String libJvmPath, String jarPath, int ramMb, String mainClass, String workDir);

    private final IJvmService.Stub binder = new IJvmService.Stub() {
        @Override
        public int startJvm(String libJvmPath, String jarPath, int ramMb, String mainClass, String workDir) throws RemoteException {
            Log.i(TAG, "Starting JVM in isolated process (PID " + Process.myPid() + ")");
            
            if (initError != null) {
                Log.e(TAG, "Cannot start JVM because koda_mchost failed to load", initError);
                return -99;
            }

            try {
                // Redirect stdin
                try {
                    java.io.File inFifo = new java.io.File(workDir, "in.fifo");
                    if (inFifo.exists()) {
                        java.io.FileInputStream fis = new java.io.FileInputStream(inFifo);
                        System.setIn(fis);
                        try {
                            android.system.Os.dup2(fis.getFD(), 0);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to redirect stdin", e);
                }
                // Process can now be killed via System.exit() normally by Minecraft.
                // We no longer intercept it because intercepting it causes zombie processes.
                
                // Samsung / Android 14 devices have strict default thread stack limits (Binder threads are 1MB).
                // Fabric's BundlerClassPathCapture requires heavy ASM parsing which causes native stack overflows.
                // By starting the JNI VM in a dedicated thread with 8MB stack, we avoid DeadObjectException crashes.
                final int[] exitCode = new int[]{-1};
                Thread jvmThread = new Thread(null, () -> {
                    exitCode[0] = startEmbeddedJvmNative(libJvmPath, jarPath, ramMb, mainClass, workDir);
                }, "EmbeddedJVM_Main", 8 * 1024 * 1024); // 8MB Stack Size
                
                jvmThread.start();
                try {
                    jvmThread.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "JVM Main thread interrupted", e);
                }
                
                return exitCode[0];
            } catch (Throwable t) {
                Log.e(TAG, "Crash during startJvm", t);
                return -98;
            }
        }

        @Override
        public void killJvm() throws RemoteException {
            Log.i(TAG, "Killing isolated JVM process (PID " + Process.myPid() + ") gracefully");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                System.setSecurityManager(null);
                System.exit(0);
            }, 500);
        }

        @Override
        public String getInitError() throws RemoteException {
            return initError != null ? initError.toString() + " | " + initError.getMessage() : "None";
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
