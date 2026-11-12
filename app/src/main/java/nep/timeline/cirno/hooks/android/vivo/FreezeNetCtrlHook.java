package nep.timeline.cirno.hooks.android.vivo;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.VivoFreezeNetCtrlWrapper;
import nep.timeline.cirno.utils.SystemChecker;

public class FreezeNetCtrlHook extends MethodHook {
    private static final String FROZEN_IM_APP = "com.android.server.am.frozen.policy.FrozenImApp";
    private static final String VPSNH_CTRL = "com.android.server.am.frozen.policy.FreezeNetCtrl2";
    private static final String VPSNH_SERVICE = "com.vivo.vpsnh.IVpsnhService";
    private static final String NETBOOSTERD_CTRL = "com.android.server.am.frozen.policy.FreezeNetCtrl1";
    private static final String NETBOOSTERD = "android.netboosterd.INetBoosterd";

    public FreezeNetCtrlHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.constructor(VPSNH_CTRL,
                        new Object[]{FROZEN_IM_APP, VPSNH_SERVICE},
                        getCaptureCallback("FreezeNetCtrl2")),
                HookTarget.constructor(NETBOOSTERD_CTRL,
                        new Object[]{FROZEN_IM_APP, NETBOOSTERD},
                        getCaptureCallback("FreezeNetCtrl1"))
        };
    }

    private CakeHooker.Callback getCaptureCallback(String label) {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                if (callback.throwable != null)
                    return;

                Object instance = callback.getThisObject();
                if (instance == null)
                    return;

                VivoFreezeNetCtrlWrapper.setInstance(instance);
                Log.i(label + " 实例已捕获");
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
