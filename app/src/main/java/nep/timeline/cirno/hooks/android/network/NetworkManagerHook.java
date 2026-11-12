package nep.timeline.cirno.hooks.android.network;

import android.os.Build;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.NetworkManagementService;

public class NetworkManagerHook extends MethodHook {
    public NetworkManagerHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method((Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) ? "com.android.server.net.NetworkManagementService" : "com.android.server.NetworkManagementService", "systemReady", new Object[0], new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                NetworkManagementService.setInstance(callback.getThisObject(), classLoader);
            }
        })
        };
    }

}
