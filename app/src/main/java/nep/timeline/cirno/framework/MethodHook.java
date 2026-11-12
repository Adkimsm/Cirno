package nep.timeline.cirno.framework;

import android.os.Build;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;

public abstract class MethodHook {
    protected static final int ANY_VERSION = -1;
    protected final ClassLoader classLoader;

    private final Object stateLock = new Object();
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();
    private final Set<Object> activeTargets = new HashSet<>();
    private boolean installed;
    private boolean closed;
    private volatile boolean hooked;

    public MethodHook(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public final void install() {
        synchronized (stateLock) {
            if (installed)
                return;
            if (closed) {
                Log.w(getClass().getSimpleName() + " 已卸载，忽略重复 install");
                return;
            }
            installed = true;
        }

        int minVersion = getMinVersion();
        if (minVersion != ANY_VERSION && Build.VERSION.SDK_INT < minVersion) {
            Log.d(getClass().getSimpleName() + " 跳过: SDK " + Build.VERSION.SDK_INT + " < " + minVersion);
            return;
        }

        HookTarget[] targets;
        try {
            targets = getTargets();
        } catch (Throwable throwable) {
            reportError(getClass().getSimpleName() + " getTargets 失败", throwable);
            return;
        }

        if (targets == null || targets.length == 0) {
            Log.d(getClass().getSimpleName() + " 没有可安装目标");
            return;
        }

        List<XposedInterface.HookHandle> installedHandles = new ArrayList<>();
        InstallPolicy policy = getInstallPolicy();
        for (HookTarget target : targets) {
            if (target == null)
                continue;
            int before = installedHandles.size();
            installTarget(target, installedHandles);
            if (policy == InstallPolicy.FIRST_SUCCESS && installedHandles.size() > before)
                break;
        }

        boolean accepted;
        synchronized (stateLock) {
            accepted = !closed;
            if (accepted) {
                handles.addAll(installedHandles);
                hooked = !handles.isEmpty();
            }
        }

        if (!accepted) {
            releaseHandles(installedHandles);
            return;
        }

        if (!hooked) {
            if (!isIgnoreError())
                Log.e(getClass().getSimpleName() + " 没有成功安装任何目标");
            return;
        }

        if (isActive()) {
            try {
                onHooked();
            } catch (Throwable throwable) {
                reportError(getClass().getSimpleName() + " onHooked 失败", throwable);
            }
        }
    }

    public final void unhook() {
        List<XposedInterface.HookHandle> snapshot;
        synchronized (stateLock) {
            if (closed)
                return;
            closed = true;
            snapshot = new ArrayList<>(handles);
            handles.clear();
            activeTargets.clear();
            hooked = false;
        }

        try {
            onUnhooked();
        } catch (Throwable throwable) {
            reportError(getClass().getSimpleName() + " onUnhooked 失败", throwable);
        }

        releaseHandles(snapshot);
    }

    public final boolean isHooked() {
        return hooked;
    }

    public final boolean isOperational() {
        return isHooked() && isReady();
    }

    protected boolean isReady() {
        return true;
    }

    protected final boolean isActive() {
        synchronized (stateLock) {
            return installed && !closed;
        }
    }

    protected final boolean isTargetHooked(Object key) {
        synchronized (stateLock) {
            return activeTargets.contains(key);
        }
    }

    protected final XposedInterface.HookHandle registerHook(Object key, Executable executable,
                                                            CakeHooker.Callback callback) {
        if (key == null || executable == null || callback == null)
            return null;

        synchronized (stateLock) {
            if (closed || activeTargets.contains(key))
                return null;
            activeTargets.add(key);
        }

        XposedInterface.HookHandle handle = null;
        try {
            executable.setAccessible(true);
            handle = CakeHooker.hook(executable, callback);
        } catch (Throwable throwable) {
            reportError(getClass().getSimpleName() + " 动态注册失败: " + executable, throwable);
        }

        boolean release = false;
        synchronized (stateLock) {
            if (handle == null || closed) {
                activeTargets.remove(key);
                release = handle != null;
            } else {
                handles.add(handle);
                hooked = true;
            }
        }

        if (release)
            releaseHandles(List.of(handle));
        return release ? null : handle;
    }

    protected abstract HookTarget[] getTargets() throws Throwable;

    protected InstallPolicy getInstallPolicy() {
        return InstallPolicy.ALL;
    }

    protected void onHooked() {
    }

    protected void onUnhooked() {
    }

    public int getMinVersion() {
        return ANY_VERSION;
    }

    public boolean isIgnoreError() {
        return false;
    }

    private void installTarget(HookTarget target, List<XposedInterface.HookHandle> out) {
        List<Executable> executables;
        try {
            executables = target.resolve(classLoader);
        } catch (Throwable throwable) {
            reportError(getClass().getSimpleName() + " 解析 " + target.label() + " 失败", throwable);
            return;
        }

        if (executables.isEmpty()) {
            Log.d(getClass().getSimpleName() + " 未找到 " + target.label());
            return;
        }

        for (Executable executable : executables) {
            CakeHooker.Callback callback;
            try {
                callback = target.callbackFor(executable);
            } catch (Throwable throwable) {
                reportError(getClass().getSimpleName() + " 创建 callback 失败: " + target.label(), throwable);
                continue;
            }
            if (callback == null)
                continue;

            try {
                executable.setAccessible(true);
                out.add(CakeHooker.hook(executable, callback));
                Log.i(target.label() + " "
                        + Arrays.toString(executable.getParameterTypes())
                        + " -> 成功Hook完毕!");
            } catch (Throwable throwable) {
                reportError(getClass().getSimpleName() + " hook 失败: " + executable, throwable);
            }
        }
    }

    private void releaseHandles(List<XposedInterface.HookHandle> releases) {
        for (XposedInterface.HookHandle handle : releases) {
            if (handle == null)
                continue;
            try {
                handle.unhook();
            } catch (Throwable throwable) {
                reportError(getClass().getSimpleName() + " 释放 hook 失败", throwable);
            }
        }
    }

    private void reportError(String message, Throwable throwable) {
        if (isIgnoreError())
            Log.d(message, throwable);
        else
            Log.e(message, throwable);
    }
}
