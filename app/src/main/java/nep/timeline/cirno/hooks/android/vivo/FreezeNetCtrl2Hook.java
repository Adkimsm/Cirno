package nep.timeline.cirno.hooks.android.vivo;

import java.util.List;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class FreezeNetCtrl2Hook extends MethodHook {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public FreezeNetCtrl2Hook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.frozen.policy.FreezeNetCtrl2$2";
    }

    @Override
    public String getTargetMethod() {
        return "onTcpPacketInput";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{
            int.class, 
            "com.vivo.vpsnh.TcpInfoParcel",
            "com.vivo.vpsnh.TcpPacketParcel"
        };
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleVivoEvents())
                    return;

                int uid = (int) callback.getArgs()[0];

                // 获取对应的 AppRecord 列表
                List<AppRecord> appRecords = AppService.getByUid(uid);
                if (appRecords.isEmpty()) {
                    callback.returnAndSkip(null);
                    return;
                }

                // 检查是否允许网络消息解冻
                boolean allowed = false;
                for (AppRecord appRecord : appRecords) {
                    if (appRecord == null)
                        continue;

                    boolean networkMessageAllowed = AppConfigs.isNetworkMessageAllowed(
                        appRecord.getPackageName(),
                        appRecord.getUserId()
                    );

                    if (!networkMessageAllowed)
                        continue;

                    allowed = true;
                    FreezerService.temporaryUnfreezeIfNeed(
                        appRecord, 
                        "Vivo Network", 
                        TEMP_UNFREEZE_INTERVAL_MS
                    );
                }

                // 如果不允许，阻止原始方法执行
                if (!allowed)
                    callback.returnAndSkip(null);
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
