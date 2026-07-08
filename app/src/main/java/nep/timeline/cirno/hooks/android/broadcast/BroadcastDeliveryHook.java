package nep.timeline.cirno.hooks.android.broadcast;

import android.content.Intent;
import android.os.Build;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.utils.SystemChecker;
import nep.timeline.cirno.virtuals.BroadcastRecord;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class BroadcastDeliveryHook extends MethodHook {
    public BroadcastDeliveryHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) ? "com.android.server.am.BroadcastQueueImpl" : "com.android.server.am.BroadcastQueue";
    }

    @Override
    public String getTargetMethod() {
        return "deliverToRegisteredReceiverLocked";
    }

    @Override
    public Object[] getTargetParam() {
        if (SystemChecker.isHuawei(classLoader))
            return new Object[]{"com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", boolean.class, int.class, "com.android.server.am.BroadcastRecordEx"};

        return new Object[]{"com.android.server.am.BroadcastRecord", "com.android.server.am.BroadcastFilter", boolean.class, int.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Object record = callback.getArgs()[0];
                if (record == null)
                    return;

                BroadcastRecord broadcastRecord = new BroadcastRecord(record);

                Object filter = callback.getArgs()[1];
                if (filter == null)
                    return;

                Object receiver = CakeReflection.getObjectField(filter, "receiverList");
                if (receiver == null)
                    return;

                Object app = CakeReflection.getObjectField(receiver, "app");
                if (app == null)
                    return;

                ProcessRecord processRecord = ProcessService.getProcessRecord(app);
                if (processRecord == null)
                    return;

                String packageName = processRecord.getPackageName();
                int userId = processRecord.getUserId();
                if (AppConfigs.isAutostartBlocked(packageName, userId)) {
                    logSkippedBroadcast(record, callback.getArgs(), processRecord, "autostartBlocked");
                    broadcastRecord.skippedDelivery((int) callback.getArgs()[3]);
                    callback.returnAndSkip(null);
                    return;
                }

                if (processRecord.isFrozen()) {
                    logSkippedBroadcast(record, callback.getArgs(), processRecord, "frozen");
                    broadcastRecord.skippedDelivery((int) callback.getArgs()[3]);
                    callback.returnAndSkip(null);
                }
            }
        };
    }

    private static void logSkippedBroadcast(Object record, Object[] args, ProcessRecord processRecord, String reason) {
        try {
            Intent intent = (Intent) CakeReflection.getObjectField(record, "intent");
            String action = intent == null ? null : intent.getAction();
            boolean ordered = args.length > 2 && args[2] instanceof Boolean && (boolean) args[2];
            int index = args.length > 3 && args[3] instanceof Integer ? (int) args[3] : -1;

            Log.d("BroadcastDeliveryHook skip: action=" + action
                    + " ordered=" + ordered
                    + " index=" + index
                    + " pkg=" + processRecord.getPackageName()
                    + " process=" + processRecord.getProcessName()
                    + " userId=" + processRecord.getUserId()
                    + " reason=" + reason);
        } catch (Throwable e) {
            Log.d("BroadcastDeliveryHook skip log failed", e);
        }
    }
}
