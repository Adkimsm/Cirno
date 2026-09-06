package nep.timeline.cirno.hooks.android.vivo;

import nep.timeline.cirno.framework.MethodHook;
import java.util.ArrayList;
import java.util.List;

public class VivoHooks {
    private final List<MethodHook> hooks = new ArrayList<>();
    private static boolean available = false;

    public VivoHooks(ClassLoader classLoader) {
        MethodHook supervisorHook = new FreezeStateSupervisorHook(classLoader);
        MethodHook networkHook = new FrozenImAppNetworkHook(classLoader);
        MethodHook netCtrlHook = new FreezeNetCtrlHook(classLoader);

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
        return available;
    }
}
