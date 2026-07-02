package nep.timeline.cirno;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.framework.XposedInstance;
import nep.timeline.cirno.utils.AutofillData;
import nep.timeline.cirno.utils.CredentialData;
import nep.timeline.cirno.utils.ForceAppStandbyListener;
import nep.timeline.cirno.utils.InputMethodData;

public class HookInit extends XposedModule {
    private static final String PROCESS_SYSTEM_SERVER = "system_server";
    private static final String PROCESS_SYSTEM_UI = "com.android.systemui";

    private boolean systemUIHooksStarted;
    private boolean systemServerHooksStarted;
    private String processName;
    private ClassLoader hostClassLoader;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        processName = param.getProcessName();
        XposedInstance.setModule(this);
        CakeHooker.setXposedModule(this);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        String packageName = param.getPackageName();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!PROCESS_SYSTEM_UI.equals(packageName) || systemUIHooksStarted) {
            return;
        }

        startSystemUIHooks(param.getClassLoader(), packageName);
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        startSystemServerHooks(param.getClassLoader(), true);
    }

    @Override
    public boolean onHotReloading(@NonNull HotReloadingParam param) {
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

        if (param.isSystemServer() || Boolean.TRUE.equals(state.get("systemServerHooksStarted"))) {
            startSystemServerHooks(hostClassLoader, false);
            ProcessService.rebuildFromSystem();
            restoreRuntimeState(state);
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
            InputMethodData.restoreState(state.get("inputMethodData"));
            AutofillData.restoreState(state.get("autofillData"));
            CredentialData.restoreState(state.get("credentialData"));
        } catch (Throwable throwable) {
            Log.w("Cirno hot reload runtime state restore failed", throwable);
        }
    }

    private static String getString(Map<?, ?> state, String key, String fallback) {
        Object value = state.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static ClassLoader getClassLoader(Object value) {
        return value instanceof ClassLoader ? (ClassLoader) value : null;
    }
}
