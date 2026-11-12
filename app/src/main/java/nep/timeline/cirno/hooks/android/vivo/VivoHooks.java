package nep.timeline.cirno.hooks.android.vivo;

import android.os.Build;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.VivoFreezeNetCtrlWrapper;

import java.util.ArrayList;
import java.util.List;

public class VivoHooks {
    private final List<MethodHook> hooks = new ArrayList<>();
    private final MethodHook freezeDenyHook;
    private final MethodHook supervisorHook;
    private final MethodHook networkHook;
    private static boolean available = false;
    // Android 12-14 的安装状态会延迟完成，需按需查询而不是缓存一次结果
    private static volatile MethodHook lowVersionSupervisorHook;
    private static volatile MethodHook lowVersionNetworkHook;

    public VivoHooks(ClassLoader classLoader) {
        lowVersionSupervisorHook = null;
        lowVersionNetworkHook = null;

        supervisorHook = new FreezeStateSupervisorHook(classLoader);
        freezeDenyHook = new VivoFreezeDenyHook(classLoader);
        MethodHook netCtrlHook;

        // Android 15+ 保持原有 FrozenImApp/FreezeNetCtrl2 路径；
        // Android 12-14 使用 netboosterd 的 AIDL 回调与控制器。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            networkHook = new FrozenImAppNetworkHook(classLoader);
            netCtrlHook = new FreezeNetCtrlHook(classLoader);
        } else {
            networkHook = new NetBoosterdNetworkHook(classLoader);
            netCtrlHook = new NetBoosterdCtrlHook(classLoader);
            lowVersionSupervisorHook = supervisorHook;
            lowVersionNetworkHook = networkHook;
        }

        hooks.add(supervisorHook);
        hooks.add(freezeDenyHook);
        hooks.add(networkHook);
        hooks.add(netCtrlHook);
    }

    public void install() {
        for (MethodHook hook : hooks) {
            hook.install();
        }
        available = supervisorHook.isHooked() || networkHook.isHooked();
    }

    /**
     * 切换冻结后端（Re-Kernel / nkBinder）时只卸载事件 hook。
     * vivo 自带的冻结入口始终被拦截，避免与新的冻结后端互相干扰。
     */
    public void unhookEventHooks() {
        for (MethodHook hook : hooks) {
            if (hook != freezeDenyHook)
                hook.unhook();
        }
    }

    public static boolean isAvailable() {
        MethodHook network = lowVersionNetworkHook;
        if (network != null) {
            MethodHook supervisor = lowVersionSupervisorHook;
            return (supervisor != null && supervisor.isHooked()) || network.isOperational();
        }
        return available;
    }

    public static boolean isNetworkAvailable() {
        MethodHook network = lowVersionNetworkHook;
        if (network != null)
            return network.isOperational() && VivoFreezeNetCtrlWrapper.getInstance() != null;

        return available && VivoFreezeNetCtrlWrapper.getInstance() != null;
    }
}
