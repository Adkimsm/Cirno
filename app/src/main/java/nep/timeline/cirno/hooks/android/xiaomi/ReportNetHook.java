package nep.timeline.cirno.hooks.android.xiaomi;

import java.util.List;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.utils.ReflectUtils;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class ReportNetHook extends MethodHook {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public ReportNetHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.miui.server.greeze.GreezeManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "reportNet";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(),
                int.class,
                long.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleMilletEvents())
                    return;

                int uid = (int) callback.getArgs()[0];

                List<AppRecord> appRecords = AppService.getByUid(uid);
                if (appRecords.isEmpty()) {
                    callback.returnAndSkip(null);
                    return;
                }

                boolean allowed = false;
                for (AppRecord appRecord : appRecords) {
                    if (appRecord == null)
                        continue;
                    Log.d("reportNet pkg: " + appRecord.getPackageName());
                    if (!AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId()))
                        continue;
                    allowed = true;
                    FreezerService.temporaryUnfreezeIfNeed(appRecord, "Network", TEMP_UNFREEZE_INTERVAL_MS);
                }

                if (!allowed)
                    callback.returnAndSkip(null);
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isXiaomi(classLoader);
    }
}
