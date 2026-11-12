package nep.timeline.cirno.hooks.android.oom;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.OomAdjService;

public class ProcessListOomAdjHook extends MethodHook {
    private static final String TARGET_CLASS = "com.android.server.am.ProcessList";
    private static final String TARGET_METHOD = "setOomAdj";

    public ProcessListOomAdjHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        return new HookTarget[] {
                HookTarget.overloads(
                        TARGET_CLASS,
                        TARGET_METHOD,
                        new Object[0][],
                        method -> method.getName().equals(TARGET_METHOD)
                                && method.getParameterTypes().length >= 3,
                        targetCallback())
        };
    }

    private CakeHooker.Callback targetCallback() {
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
    public boolean isIgnoreError() {
        return true;
    }
}
