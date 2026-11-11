package nep.timeline.cirno.hooks.android.vivo;

import android.os.Build;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.VivoFreezeNetCtrlWrapper;

import java.util.ArrayList;
import java.util.List;

public class VivoHooks {
    private final List<MethodHook> hooks = new ArrayList<>();
    private static boolean available = false;
    // Android 12-14 的安装状态会延迟完成，需按需查询而不是缓存一次结果
    private static volatile MethodHook lowVersionSupervisorHook;
    private static volatile MethodHook lowVersionNetworkHook;

    public VivoHooks(ClassLoader classLoader) {
        lowVersionSupervisorHook = null;
        lowVersionNetworkHook = null;

        MethodHook supervisorHook = new FreezeStateSupervisorHook(classLoader);
        MethodHook networkHook;
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
        hooks.add(networkHook);
        hooks.add(netCtrlHook);

        available = supervisorHook.isHooked() || networkHook.isHooked();
    }

    public void unhookAll() {
        for (MethodHook hook : hooks) {
            hook.unhook();
        }
    }

    public static boolean isAvailable() {
        MethodHook network = lowVersionNetworkHook;
        if (network != null) {
            MethodHook supervisor = lowVersionSupervisorHook;
            return (supervisor != null && supervisor.isHooked()) || network.isHooked();
        }
        return available;
    }

    public static boolean isNetworkAvailable() {
        MethodHook network = lowVersionNetworkHook;
        if (network != null)
            return network.isHooked() && VivoFreezeNetCtrlWrapper.getInstance() != null;

        return available && VivoFreezeNetCtrlWrapper.getInstance() != null;
    }
}
