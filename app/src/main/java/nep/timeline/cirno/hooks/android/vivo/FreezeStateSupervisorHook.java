package nep.timeline.cirno.hooks.android.vivo;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class FreezeStateSupervisorHook extends MethodHook {
    private static final String CMD_BBL = "BBL";  // Binder Blocked
    private static final String CMD_BFL = "BFL";  // Binder Full
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public FreezeStateSupervisorHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.vivo.services.freezer.FreezeStateSupervisor";
    }

    @Override
    public String getTargetMethod() {
        return "onEvent";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{String.class, int.class, int.class, String[].class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleVivoEvents())
                    return;

                String cmd = (String) callback.getArgs()[0];
                String[] args = (String[]) callback.getArgs()[3];

                if (args == null || args.length < 4)
                    return;

                try {
                    if (CMD_BBL.equals(cmd)) {
                        // Binder 同步调用被阻塞
                        int targetUid = Integer.parseInt(args[2]);
                        FreezerService.temporaryUnfreezeIfNeed(targetUid, "Vivo Binder", TEMP_UNFREEZE_INTERVAL_MS);

                    } else if (CMD_BFL.equals(cmd)) {
                        // Binder 缓冲区满
                        int targetUid = Integer.parseInt(args[2]);
                        FreezerService.temporaryUnfreezeIfNeed(targetUid, "Vivo BinderFull", TEMP_UNFREEZE_INTERVAL_MS);
                    }
                } catch (NumberFormatException e) {
                    // 忽略参数解析错误
                }
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
