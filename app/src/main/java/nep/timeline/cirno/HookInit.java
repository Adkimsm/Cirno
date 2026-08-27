package nep.timeline.cirno;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import nep.timeline.cirno.master.AndroidHooks;
import nep.timeline.cirno.master.SystemUIHooks;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.CachedAppOptimizer;
import nep.timeline.cirno.services.GreezeManagerServiceWrapper;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.NetworkManagementService;
import nep.timeline.cirno.services.NetworkSpeedMonitor;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.framework.XposedInstance;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.utils.AutofillData;
import nep.timeline.cirno.utils.CredentialData;
import nep.timeline.cirno.utils.ForceAppStandbyListener;
import nep.timeline.cirno.utils.InputMethodData;

public class HookInit extends XposedModule {
    private static final int MIN_XPOSED_API = 101;
    private static final String PROCESS_SYSTEM_SERVER = "system_server";
    private static final String PROCESS_SYSTEM_UI = "com.android.systemui";

    // Hook 侧源码指纹：onHotReloading 据此判断新 APK 的 hook 代码是否变化，
    // 未变化则返回 false 跳过自动热重载（纯 UI 更新不触发热重载）。
    // 手动热重载通过 extras 的 force 标记强制进行，不受指纹判断影响。
    private static final String HOOK_FINGERPRINT_ASSET = "assets/hook_fingerprint.txt";

    private boolean unsupportedXposedApi;
    private boolean systemUIHooksStarted;
    private boolean systemServerHooksStarted;
    private String processName;
    private ClassLoader hostClassLoader;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
        int apiVersion = getApiVersion();
        unsupportedXposedApi = apiVersion < MIN_XPOSED_API;
        if (unsupportedXposedApi) {
            Log.w("Cirno requires Xposed API " + MIN_XPOSED_API + " or later, current=" + apiVersion);
        }
        XposedInstance.setModule(this);
        CakeHooker.setXposedModule(this);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String packageName = param.getPackageName();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (unsupportedXposedApi) {
            return;
        }

        String packageName = param.getPackageName();
        if (!PROCESS_SYSTEM_UI.equals(packageName) || systemUIHooksStarted) {
            return;
        }

        startSystemUIHooks(param.getClassLoader(), packageName);
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        if (unsupportedXposedApi) {
            return;
        }

        startSystemServerHooks(param.getClassLoader(), true);
    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
        // 手动热重载（UI 通过 service 触发）携带 force 标记，始终强制执行，
        // 不受指纹判断影响。
        Bundle extras = param.getExtras();
        boolean forced = extras != null && extras.getBoolean("force", false);
        if (!forced) {
            // onHotReloading 运行在旧代码中，BuildConfig.HOOK_FINGERPRINT 就是
            // 当前运行代码的指纹（每进程独立、编译期固化）；从新 APK 读出待加载
            // 代码的指纹，二者相等即纯 UI 更新，返回 false 跳过自动热重载。
            String newFp = readFingerprintFromNewApk();
            if (newFp != null && newFp.equals(BuildConfig.HOOK_FINGERPRINT)) {
                Log.i("Cirno hot reload skipped: hook side unchanged (fingerprint " + newFp + ")");
                return false;
            }
            // newFp 为 null（读取失败）时不拦截，降级为执行热重载
        }

        HashMap<String, Object> state = new HashMap<>();
        state.put("processName", processName);
        state.put("hostClassLoader", hostClassLoader);
        state.put("systemServerHooksStarted", systemServerHooksStarted);
        state.put("systemUIHooksStarted", systemUIHooksStarted);
        state.put("activityManagerService", ActivityManagerService.getInstance());
        state.put("cachedAppOptimizer", CachedAppOptimizer.getInstance());
        state.put("greezeManagerService", GreezeManagerServiceWrapper.getInstance());
        state.put("networkManagementClassLoader", NetworkManagementService.getHostClassLoader());
        state.put("networkManagementNetd", NetworkManagementService.getNetdService());
        state.put("forceAppStandbyListener", ForceAppStandbyListener.getInstance());
        state.put("bootCompleted", MonitorBinderHub.isBootCompleted());
        state.put("appStates", AppService.saveAppStates());
        state.put("inputMethodData", InputMethodData.saveState());
        state.put("autofillData", AutofillData.saveState());
        state.put("credentialData", CredentialData.saveState());
        state.put("lastNetlinkUnit", nep.timeline.cirno.rekernel.ReKernel.getLastNetlinkUnit());
        param.setSavedInstanceState(state);

        if (systemServerHooksStarted) {
            AndroidHooks.stopForHotReload();
        }
        XposedInstance.clearModule();
        CakeHooker.clearXposedModule();
        CakeHooker.clearHostClassLoader();
        return true;
    }

    @Override
    public void onHotReloaded(@NonNull HotReloadedParam param) {
        XposedInstance.setModule(this);
        CakeHooker.setXposedModule(this);

        for (XposedInterface.HookHandle handle : param.getOldHookHandles()) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
            }
        }

        Object savedState = param.getSavedInstanceState();
        if (!(savedState instanceof Map<?, ?> state)) {
            Log.w("Cirno hot reload skipped: missing saved state");
            return;
        }

        processName = getString(state, "processName", param.getProcessName());
        hostClassLoader = getClassLoader(state.get("hostClassLoader"));
        if (hostClassLoader == null) {
            Log.w("Cirno hot reload skipped: missing host classloader");
            return;
        }

        GlobalVars.classLoader = hostClassLoader;
        CakeHooker.setHostClassLoader(hostClassLoader);
        restoreSystemServerState(state);

        Object lastUnit = state.get("lastNetlinkUnit");
        if (lastUnit instanceof Integer i && i >= 22 && i <= 26) {
            nep.timeline.cirno.rekernel.ReKernel.setLastNetlinkUnit(i);
        }

        if (param.isSystemServer() || Boolean.TRUE.equals(state.get("systemServerHooksStarted"))) {
            startSystemServerHooks(hostClassLoader, false);
            restoreInputMethodState(state);
            ProcessService.rebuildFromSystem();
            restoreRuntimeState(state);
            NetworkSpeedMonitor.init();
            thawCurrentInputMethod();
            MonitorBinderHub.refreshForHotReload();
            return;
        }

        if (PROCESS_SYSTEM_UI.equals(processName) || Boolean.TRUE.equals(state.get("systemUIHooksStarted"))) {
            startSystemUIHooks(hostClassLoader, PROCESS_SYSTEM_UI);
        }
    }

    private void startSystemUIHooks(ClassLoader classLoader, String packageName) {
        systemUIHooksStarted = true;
        hostClassLoader = GlobalVars.classLoader = classLoader;
        processName = packageName;
        CakeHooker.setHostClassLoader(classLoader);

        try {
            SystemUIHooks.start(classLoader);
        } catch (Throwable throwable) {
            Log.e("Cirno (" + packageName + ") -> Hook failed", throwable);
        }
    }

    private void startSystemServerHooks(ClassLoader classLoader, boolean rotateLog) {
        systemServerHooksStarted = true;
        hostClassLoader = GlobalVars.classLoader = classLoader;
        processName = PROCESS_SYSTEM_SERVER;
        CakeHooker.setHostClassLoader(classLoader);

        try {
            if (rotateLog) {
                File source = new File(GlobalVars.LOG_DIR, "current.log");
                File dest = new File(GlobalVars.LOG_DIR, "last.log");
                boolean ignoredDelete = dest.delete();
                boolean ignoredRename = source.renameTo(dest);
            }
            AndroidHooks.start(classLoader);
        } catch (Throwable throwable) {
            Log.e("Cirno (android) -> Hook failed", throwable);
        }
    }

    private void restoreSystemServerState(Map<?, ?> state) {
        ActivityManagerService.restoreInstance(state.get("activityManagerService"));
        CachedAppOptimizer.restoreInstance(state.get("cachedAppOptimizer"));
        GreezeManagerServiceWrapper.restoreInstance(state.get("greezeManagerService"));
        ForceAppStandbyListener.restoreInstance(state.get("forceAppStandbyListener"));
        ClassLoader networkClassLoader = getClassLoader(state.get("networkManagementClassLoader"));
        if (networkClassLoader != null) {
            NetworkManagementService.restoreState(networkClassLoader, state.get("networkManagementNetd"));
        }
        Object bootCompleted = state.get("bootCompleted");
        if (bootCompleted instanceof Boolean) {
            MonitorBinderHub.restoreBootCompleted((Boolean) bootCompleted);
        }
    }

    private void restoreRuntimeState(Map<?, ?> state) {
        try {
            AppService.restoreAppStates(state.get("appStates"));
            AutofillData.restoreState(state.get("autofillData"));
            CredentialData.restoreState(state.get("credentialData"));
        } catch (Throwable throwable) {
            Log.w("Cirno hot reload runtime state restore failed", throwable);
        }
    }

    private void restoreInputMethodState(Map<?, ?> state) {
        try {
            InputMethodData.restoreState(state.get("inputMethodData"));
            // method map 属于旧 IMMS 实例，恢复后通过 Settings 兜底建立当前 IME 状态。
            InputMethodData.refreshFromSettings();
        } catch (Throwable throwable) {
            Log.w("Cirno hot reload input method state restore failed", throwable);
        }
    }

    private void thawCurrentInputMethod() {
        try {
            AppRecord appRecord = InputMethodData.getCurrentInputMethodApp();
            if (appRecord != null) {
                FreezerService.thaw(appRecord);
            }
        } catch (Throwable throwable) {
            Log.w("Cirno hot reload input method thaw failed", throwable);
        }
    }

    private static String getString(Map<?, ?> state, String key, String fallback) {
        Object value = state.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static ClassLoader getClassLoader(Object value) {
        return value instanceof ClassLoader ? (ClassLoader) value : null;
    }

    // 从新 APK 的 assets/hook_fingerprint.txt 读取 hook 侧源码指纹。
    // onHotReloading 运行在旧代码中，BuildConfig.HOOK_FINGERPRINT 是旧值，
    // 必须从已被替换的新 APK 文件中读取新指纹才能判断 hook 侧是否变化。
    private String readFingerprintFromNewApk() {
        String apkPath = resolveModuleApkPath();
        if (apkPath == null) return null;
        try (ZipFile apk = new ZipFile(new File(apkPath))) {
            java.util.zip.ZipEntry entry = apk.getEntry(HOOK_FINGERPRINT_ASSET);
            if (entry == null) return null;
            try (java.io.InputStream is = apk.getInputStream(entry)) {
                byte[] all = readAll(is);
                if (all.length == 0) return "";
                return new String(all, StandardCharsets.UTF_8).trim();
            }
        } catch (Throwable t) {
            Log.w("Cirno readFingerprintFromNewApk failed (apk=" + apkPath + ")", t);
            return null;
        }
    }

    // 解析当前已安装模块 APK 路径。优先用 PackageManager 实时查询，避免
    // getModuleApplicationInfo() 返回加载时缓存的旧路径（更新后旧 APK 路径
    // 已被删除，ZipFile 会失败）。system_server 有 AMS 上下文可查任意包；
    // 其它进程（如 systemui）回落到 getModuleApplicationInfo()。
    private String resolveModuleApkPath() {
        try {
            String pmPath = resolveApkPathViaPackageManager();
            if (pmPath != null) return pmPath;
        } catch (Throwable t) {
            Log.w("Cirno resolveApkPathViaPackageManager failed", t);
        }
        try {
            ApplicationInfo info = getModuleApplicationInfo();
            if (info != null && info.sourceDir != null) return info.sourceDir;
        } catch (Throwable t) {
            Log.w("Cirno getModuleApplicationInfo failed", t);
        }
        return null;
    }

    private String resolveApkPathViaPackageManager() {
        android.content.Context context = ActivityManagerService.getContext();
        if (context == null) return null;
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            if (pm == null) return null;
            android.content.pm.PackageInfo info = pm.getPackageInfo(GlobalVars.PACKAGE_NAME, 0);
            if (info.applicationInfo != null && info.applicationInfo.sourceDir != null) {
                return info.applicationInfo.sourceDir;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static byte[] readAll(java.io.InputStream is) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(128);
        byte[] buf = new byte[128];
        int n;
        while ((n = is.read(buf)) > 0) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}
