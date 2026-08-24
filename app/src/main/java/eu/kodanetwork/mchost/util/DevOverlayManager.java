package eu.kodanetwork.mchost.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import eu.kodanetwork.mchost.R;

public class DevOverlayManager implements Application.ActivityLifecycleCallbacks {

    private static DevOverlayManager instance;
    private boolean isEnabled = false;
    private View overlayView;
    private Handler handler = new Handler(Looper.getMainLooper());

    private float lastX = 50f;
    private float lastY = 200f;
    private float dX, dY;

    private TextView tvArch, tvRamTotal, tvRamAvail, tvHeapMax;

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isEnabled && overlayView != null && tvArch != null) {
                updateStats(overlayView.getContext());
                handler.postDelayed(this, 1000);
            }
        }
    };

    private DevOverlayManager() {
    }

    public static DevOverlayManager getInstance() {
        if (instance == null) {
            instance = new DevOverlayManager();
        }
        return instance;
    }

    public void init(Application app) {
        app.registerActivityLifecycleCallbacks(this);
    }

    public void toggle(Activity currentActivity) {
        isEnabled = !isEnabled;
        if (isEnabled) {
            attachToActivity(currentActivity);
        } else {
            detachFromActivity(currentActivity);
        }
    }

    private void attachToActivity(Activity activity) {
        if (activity == null || !isEnabled) return;
        
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        
        // Remove if already attached
        if (overlayView != null && overlayView.getParent() != null) {
            ((ViewGroup) overlayView.getParent()).removeView(overlayView);
        }

        if (overlayView == null) {
            overlayView = LayoutInflater.from(activity).inflate(R.layout.layout_hw_stats_overlay, decorView, false);
            setupViews();
        }

        decorView.addView(overlayView);
        
        // Restore position
        overlayView.setX(lastX);
        overlayView.setY(lastY);

        // Start updating
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable);
    }

    private void detachFromActivity(Activity activity) {
        if (overlayView != null && overlayView.getParent() != null) {
            ((ViewGroup) overlayView.getParent()).removeView(overlayView);
        }
        handler.removeCallbacks(updateRunnable);
    }

    private void setupViews() {
        tvArch = overlayView.findViewById(R.id.tv_stat_arch);
        tvRamTotal = overlayView.findViewById(R.id.tv_stat_ram_total);
        tvRamAvail = overlayView.findViewById(R.id.tv_stat_ram_avail);
        tvHeapMax = overlayView.findViewById(R.id.tv_stat_heap_max);
        ImageView btnClose = overlayView.findViewById(R.id.overlay_btn_close);

        btnClose.setOnClickListener(v -> {
            isEnabled = false;
            if (overlayView.getParent() != null) {
                ((ViewGroup) overlayView.getParent()).removeView(overlayView);
            }
            handler.removeCallbacks(updateRunnable);
        });

        overlayView.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    lastX = event.getRawX() + dX;
                    lastY = event.getRawY() + dY;
                    view.animate()
                        .x(lastX)
                        .y(lastY)
                        .setDuration(0)
                        .start();
                    break;
            }
            return true;
        });
    }

    private void updateStats(Context context) {
        android.app.ActivityManager actManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        
        long totalRam = memInfo.totalMem / (1024 * 1024);
        long availRam = memInfo.availMem / (1024 * 1024);
        String arch = System.getProperty("os.arch");
        long maxHeap = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        
        tvArch.setText("Arch: " + arch);
        tvRamTotal.setText("Total RAM: " + totalRam + " MB");
        tvRamAvail.setText("Avail RAM: " + availRam + " MB");
        tvHeapMax.setText("Dalvik Max: " + maxHeap + " MB");
    }

    // --- Lifecycle Callbacks ---

    @Override
    public void onActivityResumed(Activity activity) {
        if (isEnabled) {
            attachToActivity(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        detachFromActivity(activity);
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {
        if (overlayView != null && overlayView.getContext() == activity) {
            // Nullify context reference to avoid memory leaks
            overlayView = null;
        }
    }
}
