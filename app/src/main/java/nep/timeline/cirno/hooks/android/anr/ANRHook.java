package nep.timeline.cirno.hooks.android.anr;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.utils.AnrHelper;

public class ANRHook extends MethodHook {
    public ANRHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.method("com.android.server.am.AnrHelper$AnrRecord", "appNotResponding", new Object[]{boolean.class}, new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Object app = CakeReflection.getObjectField(callback.getThisObject(), "mApp");
                if (app == null)
                    return;
                AnrHelper.processingAnr(callback, app);
            }
        })
        };
    }

}
