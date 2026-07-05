package nep.timeline.cirno.hooks.android.activity;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.BootFreezeService;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.NetworkSpeedMonitor;
import nep.timeline.cirno.utils.InputMethodData;
import nep.timeline.cirno.utils.ReflectUtils;

public class ActivityManagerSystemReadyHook extends MethodHook {
    public ActivityManagerSystemReadyHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.ActivityManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "systemReady";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(), Runnable.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                MonitorBinderHub.setBootCompleted();
                MonitorBinderHub.publish("ActivityManagerService.systemReady");
                NetworkSpeedMonitor.init();
                InputMethodData.refreshFromSettings();
                if (GlobalVars.globalSettings != null && GlobalVars.globalSettings.bootFreezeAll) {
                    BootFreezeService.freezeAll();
                }
            }
        };
    }
}
