/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.morphe.extension.shared.Utils;

public final class SettingsShortcutActivity extends Activity {
    private static final String TAG = "PikoSettingsShortcut";
    private static final String ACTION_OPEN_PIKO_SETTINGS =
            "app.morphe.extension.instagram.action.OPEN_PIKO_SETTINGS";
    private static final long LAUNCH_TIMEOUT_MILLIS = 15_000L;
    private static final Object PENDING_LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static PendingOpen pendingOpen;

    private static boolean acceptsAction(String action) {
        return ACTION_OPEN_PIKO_SETTINGS.equals(action);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent source = getIntent();
            if (source != null && acceptsAction(source.getAction())) {
                clearPendingOpen();
                Activity nativeActivity = Utils.getActivity();
                if (nativeActivity != null && !nativeActivity.isFinishing()
                        && !nativeActivity.isDestroyed()) {
                    openSettings(this);
                } else {
                    boolean deferred = false;
                    try {
                        deferred = openAfterLauncherInitialization();
                    } catch (RuntimeException exception) {
                        Log.e(TAG, "Failed to prepare Instagram launcher initialization",
                                exception);
                    }
                    if (!deferred) openSettings(this);
                }
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to open Piko settings from the launcher shortcut", exception);
        } finally {
            finish();
        }
    }

    private boolean openAfterLauncherInitialization() {
        PackageManager packageManager = getPackageManager();
        String packageName = getPackageName();
        Intent installedLaunch = packageManager.getLaunchIntentForPackage(packageName);
        ComponentName launcher = installedLaunch == null ? null : installedLaunch.getComponent();
        if (launcher == null || !packageName.equals(launcher.getPackageName())) return false;

        ComponentName target = launcher;
        try {
            ActivityInfo activityInfo = packageManager.getActivityInfo(launcher, 0);
            if (activityInfo.targetActivity != null) {
                target = new ComponentName(packageName, activityInfo.targetActivity);
            }
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(TAG, "Installed Instagram launcher activity was not found", exception);
            return false;
        }

        Application application = getApplication();
        PendingOpen request = new PendingOpen(application, launcher, target);
        if (!request.install()) return false;

        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(launcher)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(launch);
            return true;
        } catch (RuntimeException exception) {
            request.claim();
            Log.e(TAG, "Failed to initialize Instagram before opening settings", exception);
            return false;
        }
    }

    private static void openSettings(Context context) {
        Intent target = new Intent(context, SettingsActivity.class);
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof Activity)) target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(target);
    }

    private static void clearPendingOpen() {
        synchronized (PENDING_LOCK) {
            if (pendingOpen != null) pendingOpen.clearLocked();
        }
    }

    private static final class PendingOpen implements Application.ActivityLifecycleCallbacks,
            Runnable {
        private final Application application;
        private final ComponentName launcher;
        private final ComponentName target;

        private PendingOpen(Application application, ComponentName launcher, ComponentName target) {
            this.application = application;
            this.launcher = launcher;
            this.target = target;
        }

        private boolean install() {
            synchronized (PENDING_LOCK) {
                if (pendingOpen != null) pendingOpen.clearLocked();
                boolean registered = false;
                try {
                    application.registerActivityLifecycleCallbacks(this);
                    registered = true;
                    pendingOpen = this;
                    if (!MAIN_HANDLER.postDelayed(this, LAUNCH_TIMEOUT_MILLIS)) {
                        throw new IllegalStateException("Could not schedule launcher timeout");
                    }
                    return true;
                } catch (RuntimeException exception) {
                    MAIN_HANDLER.removeCallbacks(this);
                    if (registered) {
                        try {
                            application.unregisterActivityLifecycleCallbacks(this);
                        } catch (RuntimeException cleanupException) {
                            Log.e(TAG, "Failed to clean launcher observer registration",
                                    cleanupException);
                        }
                    }
                    if (pendingOpen == this) pendingOpen = null;
                    Log.e(TAG, "Failed to watch Instagram launcher initialization", exception);
                    return false;
                }
            }
        }

        private boolean claim() {
            synchronized (PENDING_LOCK) {
                if (pendingOpen != this) return false;
                clearLocked();
                return true;
            }
        }

        private void clearLocked() {
            MAIN_HANDLER.removeCallbacks(this);
            try {
                application.unregisterActivityLifecycleCallbacks(this);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Failed to clear Instagram launcher observer", exception);
            }
            if (pendingOpen == this) pendingOpen = null;
        }

        private boolean matches(Activity activity) {
            ComponentName resumed = activity.getComponentName();
            if (launcher.equals(resumed) || target.equals(resumed)) return true;
            Intent intent = activity.getIntent();
            ComponentName requested = intent == null ? null : intent.getComponent();
            return launcher.equals(requested) || target.equals(requested);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (!matches(activity) || !claim()) return;
            try {
                openSettings(activity);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Failed to open settings after Instagram initialization", exception);
                try {
                    openSettings(application);
                } catch (RuntimeException fallbackException) {
                    Log.e(TAG, "Failed to open Piko settings fallback", fallbackException);
                }
            }
        }

        @Override
        public void run() {
            if (!claim()) return;
            Log.w(TAG, "Timed out waiting for Instagram launcher initialization");
        }

        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    }
}
