package nep.timeline.cirno.services;

import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;

public class GreezeManagerServiceWrapper {
    public static volatile Object instance;

    public static void setInstance(Object obj) {
        instance = obj;
    }

    public static Object getInstance() {
        return instance;
    }

    public static void restoreInstance(Object obj) {
        instance = obj;
    }

    public static void monitorNet(int uid) {
        if (instance == null)
            return;
        try {
            CakeReflection.callMethod(instance, "monitorNet", uid);
            Log.d(uid + " monitorNet");
        } catch (Throwable throwable) {
            Log.e("monitorNet", throwable);
        }
    }

    public static void clearMonitorNet(int uid) {
        if (instance == null)
            return;
        try {
            CakeReflection.callMethod(instance, "clearMonitorNet", uid);
            Log.d(uid + " clearMonitorNet");
        } catch (Throwable throwable) {
            Log.e("clearMonitorNet", throwable);
        }
    }
}
