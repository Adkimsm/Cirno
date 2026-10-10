package nep.timeline.cirno.hooks.android.oom;

import java.lang.reflect.Method;

import nep.timeline.cirno.framework.OverloadMethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.OomAdjService;

public class ProcessListOomAdjHook extends OverloadMethodHook {
    public ProcessListOomAdjHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.ProcessList";
    }

    @Override
    public String getTargetMethod() {
        return "setOomAdj";
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                Object[] args = callback.getArgs();
                if (args.length < 3 || !(args[0] instanceof Integer)) {
                    return;
                }
                OomAdjService.applyForPidAsync((Integer) args[0]);
            }
        };
    }

    @Override
    protected boolean shouldHookFallback(Method method) {
        return super.shouldHookFallback(method)
                && method.getParameterTypes().length >= 3;
    }

    @Override
    protected HookMode getHookMode() {
        return HookMode.AFTER;
    }

    @Override
    public boolean isIgnoreError() {
        return true;
    }
}
