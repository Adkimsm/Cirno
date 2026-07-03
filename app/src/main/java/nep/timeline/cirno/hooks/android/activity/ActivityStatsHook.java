package nep.timeline.cirno.hooks.android.activity;

import android.app.usage.UsageEvents;
import android.os.IBinder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.services.OomAdjService;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.virtuals.ActivityRecord;

public class ActivityStatsHook extends MethodHook {
    private final Map<IBinder, ActivityRecord> activityRecords = new ConcurrentHashMap<>();
    private final List<Integer> events = List.of(UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, UsageEvents.Event.ACTIVITY_STOPPED + 1);

    public ActivityStatsHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.wm.ActivityTaskManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "updateActivityUsageStats";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{"com.android.server.wm.ActivityRecord", int.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                int event = (int) callback.getArgs()[1];

                if (!events.contains(event))
                    return;

                Object activityObject = callback.getArgs()[0];
                if (activityObject == null)
                    return;

                ActivityRecord record = new ActivityRecord(activityObject);

                ActivityRecord activityRecord = (event == UsageEvents.Event.ACTIVITY_RESUMED || event == UsageEvents.Event.ACTIVITY_PAUSED) ? activityRecords.computeIfAbsent(record.getToken(), k -> record) : activityRecords.remove(record.getToken());

                if (activityRecord == null)
                    return;

                AppRecord appRecord = activityRecord.toAppRecord();

                if (appRecord == null)
                    return;

                boolean changed = event == UsageEvents.Event.ACTIVITY_RESUMED || event == UsageEvents.Event.ACTIVITY_PAUSED
                        ? appRecord.getAppState().addActivity(activityRecord.getToken())
                        : appRecord.getAppState().removeActivity(activityRecord.getToken());

                if (!changed)
                    return;

                if (appRecord.getAppState().isVisible()) {
                    Log.d(appRecord.getPackageNameWithUser() + " 进入前台");
                    FreezerService.thaw(appRecord);
                } else {
                    Log.d(appRecord.getPackageNameWithUser() + " 进入后台");
                    OomAdjService.applyForAppAsync(appRecord);
                    FreezerHandler.sendFreezeMessage(appRecord);
                }
            }
        };
    }
}
