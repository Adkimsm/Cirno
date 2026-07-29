package nep.timeline.cirno.services;

import android.os.Build;

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
        if (Build.VERSION.SDK_INT < 34) {
            try {
                CakeReflection.callMethod(instance, "nAddConcernedUid", uid);
                Log.d(uid + " monitorNet (GreezeManagerService)");
            } catch (Throwable throwable) {
                Log.e("monitorNet", throwable);
            }
        } else if (Build.VERSION.SDK_INT == 34) {
            try {
                CakeReflection.callMethod(instance, "nAddConcernedUid", uid);
                Log.d(uid + " monitorNet (GreezeManagerService)");
            } catch (Throwable ignored) {
            }
            try {
                ClassLoader cl = instance.getClass().getClassLoader();
                Class<?> nativeClass = CakeReflection.findClass(
                        "com.miui.server.greeze.GreezeManagerServiceNative", cl);
                CakeReflection.callStaticMethod(nativeClass, "nAddConcernedUid", uid);
                Log.d(uid + " monitorNet (direct JNI)");
            } catch (Throwable ignored) {
            }
        } else {
            try {
                ClassLoader cl = instance.getClass().getClassLoader();
                Class<?> nativeClass = CakeReflection.findClass(
                        "com.miui.server.greeze.GreezeManagerServiceNative", cl);
                CakeReflection.callStaticMethod(nativeClass, "nAddConcernedUid", uid);
                Log.d(uid + " monitorNet (direct JNI)");
            } catch (Throwable throwable) {
                Log.e("monitorNet", throwable);
            }
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
