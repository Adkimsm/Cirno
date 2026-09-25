package nep.timeline.cirno.utils;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.services.ProcessService;
import nep.timeline.cirno.virtuals.ProcessRecord;

public class AnrHelper {
    public static void processingAnr(CakeHooker.BeforeHookCallback callback, Object app) {
        if (app == null)
            return;
        ProcessRecord processRecord = ProcessService.getProcessRecord(app);
        if (processRecord == null)
            return;
        AppRecord appRecord = processRecord.getAppRecord();
        if (appRecord == null)
            return;
        if (!appRecord.isSystem())
            callback.returnAndSkip(null);
    }
}
