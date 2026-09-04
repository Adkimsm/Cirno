package nep.timeline.cirno.hooks.android.broadcast;

import android.content.Intent;
import android.os.Build;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.ReflectUtils;
import nep.timeline.cirno.utils.SystemChecker;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class BroadcastSkipHook extends MethodHook {
    private static final String ACTION_FAIR_MEMORY_TRIM = "itgsa.intent.action.TRIM";
    private static final String ACTION_FAIR_MEMORY_KILL = "itgsa.intent.action.KILL";
    private static final long FAIR_MEMORY_UNFREEZE_INTERVAL_MS = 3000L;

    public BroadcastSkipHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.BroadcastSkipPolicy";
    }

    @Override
    public String getTargetMethod() {
        return "shouldSkipMessage";
    }

    @Override
    public Object[] getTargetParam() {
        // vivo Android 16+: 6 参数版本 (BroadcastRecord, BroadcastFilter, boolean, boolean, int, IVivoBroadcastQueueModern)
        if (SystemChecker.isVivo(classLoader) && Build.VERSION.SDK_INT >= 36)
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(), "com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", boolean.class, boolean.class, int.class, "com.android.server.am.IVivoBroadcastQueueModern");
        // vivo 旧版本: 5 参数版本 (BroadcastRecord, BroadcastFilter, boolean, int, IVivoBroadcastQueueModern)
        if (SystemChecker.isVivo(classLoader))
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(), "com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", boolean.class, int.class, "com.android.server.am.IVivoBroadcastQueueModern");
        // Android 15+ 非 vivo: 3 参数版本 (BroadcastRecord, BroadcastFilter, boolean)
        if (Build.VERSION.SDK_INT >= 36)
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(), "com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", boolean.class);
        // Android 14 及以下: 2 参数版本 (BroadcastRecord, BroadcastFilter)
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(), "com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter");
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    if (callback.result != null) {
                        return;
                    }

                    Object record = callback.getArgs()[0];
                    if (record == null) {
                        return;
                    }

                    Intent intent = (Intent) CakeReflection.getObjectField(record, "intent");
                    String action = intent == null ? null : intent.getAction();

                    Object filter = callback.getArgs()[1];
                    if (filter == null) {
                        return;
                    }

                    if (isFairMemoryBroadcast(action)) {
                        postFairMemoryTemporaryUnfreeze(filter, action);
                    }

                    Object receiver = CakeReflection.getObjectField(filter, "receiverList");
                    if (receiver == null) {
                        return;
                    }

                    Object app = CakeReflection.getObjectField(receiver, "app");
                    if (app == null) {
                        return;
                    }

                    ProcessRecord processRecord = ProcessService.getProcessRecord(app);
                    if (processRecord == null) {
                        return;
                    }

                    String packageName = processRecord.getPackageName();
                    int userId = processRecord.getUserId();
                    if (AppConfigs.isAutostartBlocked(packageName, userId)) {
                        callback.result = "Skipping deliver [Cirno]: autostart blocked";
                        return;
                    }

                    if (processRecord.isFrozen()) {
                        if (isFairMemoryBroadcast(action)) {
                            return;
                        }
                        callback.result = "Skipping deliver [Cirno]: frozen process";
                    }
                } catch (Exception e) {
                    Log.e("BroadcastSkipHook 处理失败", e);
                }
            }
        };
    }

    @Override
    public int getMinVersion() {
        return Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    private static boolean isFairMemoryBroadcast(String action) {
        return ACTION_FAIR_MEMORY_TRIM.equals(action) || ACTION_FAIR_MEMORY_KILL.equals(action);
    }

    private static void postFairMemoryTemporaryUnfreeze(Object filter, String action) {
        Handlers.broadcast.post(() -> {
            try {
                Object receiver = CakeReflection.getObjectField(filter, "receiverList");
                if (receiver == null) {
                    return;
                }

                Object app = CakeReflection.getObjectField(receiver, "app");
                if (app == null) {
                    return;
                }

                ProcessRecord processRecord = ProcessService.getProcessRecord(app);
                if (processRecord == null || !processRecord.isFrozen()) {
                    return;
                }

                Log.d("FairMemory 广播临时解冻: action=" + action
                        + " pkg=" + processRecord.getPackageName()
                        + " process=" + processRecord.getProcessName()
                        + " userId=" + processRecord.getUserId());

                FreezerService.temporaryUnfreezeIfNeed(
                        processRecord.getPackageName(),
                        processRecord.getUserId(),
                        "FairMemory " + action,
                        FAIR_MEMORY_UNFREEZE_INTERVAL_MS
                );
            } catch (Throwable throwable) {
                Log.e("FairMemory 广播临时解冻失败", throwable);
            }
        });
    }
}
