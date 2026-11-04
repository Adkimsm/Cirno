package nep.timeline.cirno.utils;

import nep.timeline.cirno.reflect.CakeReflection;
import lombok.Setter;
import nep.timeline.cirno.entity.AppRecord;

public class ForceAppStandbyListener {
    @Setter
    private static Object instance;

    public static Object getInstance() {
        return instance;
    }

    public static void restoreInstance(Object obj) {
        instance = obj;
    }

    public static void removeAlarmsForUid(AppRecord appRecord) {
        if (instance == null)
            return;

        CakeReflection.callMethod(instance, "removeAlarmsForUid", appRecord.getUid());
    }
}
