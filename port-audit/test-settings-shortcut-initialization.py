from pathlib import Path
import os
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
PRODUCTION = ROOT / "extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/SettingsShortcutActivity.java"

files = {
    "app/morphe/extension/instagram/settings/SettingsShortcutActivity.java": PRODUCTION.read_text(),
    "android/os/Bundle.java": "package android.os; public class Bundle {}",
    "android/os/Looper.java": """
package android.os;
public final class Looper { public static Looper getMainLooper() { return new Looper(); } }
""",
    "android/os/Handler.java": """
package android.os;
import java.util.ArrayList;
import java.util.List;
public final class Handler {
    private static final List<Runnable> tasks = new ArrayList<>();
    public Handler(Looper looper) {}
    public boolean postDelayed(Runnable task, long delay) { tasks.add(task); return true; }
    public void removeCallbacks(Runnable task) { tasks.remove(task); }
    public static void runAll() {
        for (Runnable task : new ArrayList<>(tasks)) task.run();
        tasks.clear();
    }
}
""",
    "android/util/Log.java": """
package android.util;
public final class Log {
    public static int e(String tag, String message, Throwable error) { return 0; }
    public static int w(String tag, String message) { return 0; }
}
""",
    "android/content/ComponentName.java": """
package android.content;
public final class ComponentName {
    private final String packageName;
    private final String className;
    public ComponentName(String packageName, String className) {
        this.packageName = packageName; this.className = className;
    }
    public String getPackageName() { return packageName; }
    public String getClassName() { return className; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ComponentName)) return false;
        ComponentName value = (ComponentName) other;
        return packageName.equals(value.packageName) && className.equals(value.className);
    }
    @Override public int hashCode() { return packageName.hashCode() * 31 + className.hashCode(); }
}
""",
    "android/content/Intent.java": """
package android.content;
public class Intent {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_RESET_TASK_IF_NEEDED = 0x00200000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    private String action;
    private ComponentName component;
    private int flags;
    private String category;
    private boolean hasExtras;
    private String data;
    public Intent() {}
    public Intent(String action) { this.action = action; }
    public Intent(Context context, Class<?> type) {
        component = new ComponentName(context.getPackageName(), type.getName());
    }
    public String getAction() { return action; }
    public Intent setAction(String action) { this.action = action; return this; }
    public ComponentName getComponent() { return component; }
    public Intent setComponent(ComponentName value) { component = value; return this; }
    public Intent addCategory(String value) { category = value; return this; }
    public String getCategory() { return category; }
    public Intent addFlags(int value) { flags |= value; return this; }
    public int getFlags() { return flags; }
    public boolean hasExtras() { return hasExtras; }
    public String getDataString() { return data; }
}
""",
    "android/content/Context.java": """
package android.content;
import android.content.pm.PackageManager;
public class Context {
    protected String packageName = "com.instagram.android.exampleclone";
    protected PackageManager packageManager;
    public String getPackageName() { return packageName; }
    public PackageManager getPackageManager() { return packageManager; }
    public void startActivity(Intent intent) {}
}
""",
    "android/content/pm/ActivityInfo.java": """
package android.content.pm;
public class ActivityInfo { public String targetActivity; }
""",
    "android/content/pm/PackageManager.java": """
package android.content.pm;
import android.content.ComponentName;
import android.content.Intent;
public class PackageManager {
    public static class NameNotFoundException extends Exception {}
    public Intent launchIntent;
    public ActivityInfo activityInfo;
    public boolean missing;
    public Intent getLaunchIntentForPackage(String packageName) { return launchIntent; }
    public ActivityInfo getActivityInfo(ComponentName component, int flags)
            throws NameNotFoundException {
        if (missing) throw new NameNotFoundException();
        return activityInfo;
    }
}
""",
    "android/app/Application.java": """
package android.app;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
public class Application extends Context {
    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, Bundle state);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, Bundle state);
        void onActivityDestroyed(Activity activity);
    }
    public final List<ActivityLifecycleCallbacks> callbacks = new ArrayList<>();
    public final List<Intent> starts = new ArrayList<>();
    public void configure(String name, android.content.pm.PackageManager manager) {
        packageName = name; packageManager = manager;
    }
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        callbacks.add(callback);
    }
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        callbacks.remove(callback);
    }
    @Override public void startActivity(Intent intent) { starts.add(intent); }
    public void resume(Activity activity) {
        for (ActivityLifecycleCallbacks callback : new ArrayList<>(callbacks)) {
            callback.onActivityResumed(activity);
        }
    }
}
""",
    "android/app/Activity.java": """
package android.app;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
public class Activity extends Context {
    protected Application application;
    protected Intent intent;
    protected ComponentName component;
    public final List<Intent> starts = new ArrayList<>();
    public boolean finished;
    public boolean finishing;
    public boolean destroyed;
    public boolean failMainLaunch;
    protected void onCreate(Bundle state) {}
    public void configure(Application app, PackageManager manager, Intent value,
            ComponentName component) {
        application = app; packageManager = manager; packageName = app.getPackageName();
        intent = value; this.component = component;
    }
    public Intent getIntent() { return intent; }
    public ComponentName getComponentName() { return component; }
    public Application getApplication() { return application; }
    public boolean isFinishing() { return finishing; }
    public boolean isDestroyed() { return destroyed; }
    public void finish() { finished = true; }
    @Override public void startActivity(Intent value) {
        if (failMainLaunch && Intent.ACTION_MAIN.equals(value.getAction())) {
            throw new RuntimeException("main launch failure");
        }
        starts.add(value);
    }
}
""",
    "app/morphe/extension/shared/Utils.java": """
package app.morphe.extension.shared;
import android.app.Activity;
public final class Utils {
    public static Activity activity;
    public static Activity getActivity() { return activity; }
}
""",
    "app/morphe/extension/instagram/settings/SettingsActivity.java": """
package app.morphe.extension.instagram.settings;
public final class SettingsActivity {}
""",
    "app/morphe/extension/instagram/settings/SettingsShortcutControlFlowTest.java": """
package app.morphe.extension.instagram.settings;
import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import app.morphe.extension.shared.Utils;
public final class SettingsShortcutControlFlowTest {
    private static final String PKG = "com.instagram.android.exampleclone";
    private static final String ALIAS = "com.instagram.android.activity.MainTabActivity";
    private static final String REAL = "com.instagram.mainactivity.InstagramMainActivity";
    private static final String ACTION =
            "app.morphe.extension.instagram.action.OPEN_PIKO_SETTINGS";
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static PackageManager manager(ComponentName component) {
        PackageManager manager = new PackageManager();
        manager.launchIntent = new Intent(Intent.ACTION_MAIN).setComponent(component);
        manager.activityInfo = new ActivityInfo();
        manager.activityInfo.targetActivity = REAL;
        return manager;
    }
    private static SettingsShortcutActivity shortcut(Application app, PackageManager manager) {
        SettingsShortcutActivity value = new SettingsShortcutActivity();
        value.configure(app, manager, new Intent(ACTION),
                new ComponentName(PKG, SettingsShortcutActivity.class.getName()));
        return value;
    }
    private static boolean isSettings(Intent value) {
        return value.getComponent() != null
                && SettingsActivity.class.getName().equals(value.getComponent().getClassName());
    }
    public static void main(String[] args) {
        ComponentName alias = new ComponentName(PKG, ALIAS);
        PackageManager manager = manager(alias);
        Application app = new Application();
        app.configure(PKG, manager);

        Activity warm = new Activity();
        warm.configure(app, manager, new Intent(), new ComponentName(PKG, REAL));
        Utils.activity = warm;
        SettingsShortcutActivity warmShortcut = shortcut(app, manager);
        warmShortcut.onCreate(new Bundle());
        require(warmShortcut.finished && warmShortcut.starts.size() == 1
                && isSettings(warmShortcut.starts.get(0)), "warm direct path changed");
        require(app.callbacks.isEmpty(), "warm path registered a callback");

        Utils.activity = null;
        SettingsShortcutActivity cold = shortcut(app, manager);
        cold.onCreate(new Bundle());
        require(cold.finished && cold.starts.size() == 1, "cold launcher was not started");
        Intent launched = cold.starts.get(0);
        require(Intent.ACTION_MAIN.equals(launched.getAction())
                && Intent.CATEGORY_LAUNCHER.equals(launched.getCategory())
                && alias.equals(launched.getComponent()), "cold launch was not exact launcher intent");
        require((launched.getFlags() & (Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        == (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                && (launched.getFlags() & (Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) == 0,
                "cold launch used task-reuse flags instead of in-task launcher flags");
        require(!launched.hasExtras() && launched.getDataString() == null,
                "source payload leaked into launcher intent");
        require(app.callbacks.size() == 1, "cold path did not register exactly one callback");

        Activity real = new Activity();
        real.configure(app, manager, new Intent().setComponent(alias),
                new ComponentName(PKG, REAL));
        app.resume(real);
        app.resume(real);
        require(app.callbacks.isEmpty(), "successful callback was not unregistered");
        require(real.starts.size() == 1 && isSettings(real.starts.get(0)),
                "resume did not open settings exactly once");

        SettingsShortcutActivity first = shortcut(app, manager);
        SettingsShortcutActivity second = shortcut(app, manager);
        first.onCreate(new Bundle());
        second.onCreate(new Bundle());
        require(app.callbacks.size() == 1, "repeated cold request retained stale callbacks");
        app.resume(real);
        require(real.starts.size() == 2, "replacement callback did not dispatch once");

        SettingsShortcutActivity staleCold = shortcut(app, manager);
        staleCold.onCreate(new Bundle());
        require(app.callbacks.size() == 1, "stale warm setup missing callback");
        Utils.activity = warm;
        SettingsShortcutActivity becameWarm = shortcut(app, manager);
        becameWarm.onCreate(new Bundle());
        require(app.callbacks.isEmpty() && becameWarm.starts.size() == 1
                && isSettings(becameWarm.starts.get(0)),
                "warm request did not clear stale cold callback");
        int beforeResume = real.starts.size();
        app.resume(real);
        require(real.starts.size() == beforeResume, "stale cold callback dispatched twice");
        Utils.activity = null;

        PackageManager foreign = manager(new ComponentName("foreign.package", ALIAS));
        app.configure(PKG, foreign);
        SettingsShortcutActivity rejected = shortcut(app, foreign);
        rejected.onCreate(new Bundle());
        require(rejected.starts.size() == 1 && isSettings(rejected.starts.get(0))
                && app.callbacks.isEmpty(), "foreign launcher did not fail closed to direct settings");

        app.configure(PKG, manager);
        SettingsShortcutActivity failed = shortcut(app, manager);
        failed.failMainLaunch = true;
        failed.onCreate(new Bundle());
        require(app.callbacks.isEmpty() && failed.starts.size() == 1
                && isSettings(failed.starts.get(0)), "launch failure leaked callback or skipped fallback");

        SettingsShortcutActivity timeout = shortcut(app, manager);
        timeout.onCreate(new Bundle());
        require(app.callbacks.size() == 1, "timeout setup missing callback");
        Handler.runAll();
        require(app.callbacks.isEmpty() && app.starts.isEmpty(),
                "timeout did not clear quietly");

        System.out.println("8 settings shortcut control-flow checks passed");
    }
}
""",
}

with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    sources = []
    for relative, source in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        sources.append(path)
    environment = os.environ.copy()
    subprocess.run(["javac", "--release", "17", "-d", str(root), *map(str, sources)],
                   check=True, env=environment)
    subprocess.run(["java", "-cp", str(root),
                    "app.morphe.extension.instagram.settings.SettingsShortcutControlFlowTest"],
                   check=True, env=environment)
