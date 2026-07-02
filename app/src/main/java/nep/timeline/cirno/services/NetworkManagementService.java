package nep.timeline.cirno.services;

import android.os.Build;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Set;

import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.log.Log;

public class NetworkManagementService {
    public static volatile ClassLoader instance;
    private static Object mNetdService;
    private static Class<?> UidRangeParcel;

    public static void setInstance(Object obj, ClassLoader classLoader) {
        instance = classLoader;
        if (Build.VERSION.SDK_INT > 35) return;
        mNetdService = CakeReflection.getObjectField(obj, "mNetdService");
        UidRangeParcel = CakeReflection.findClass("android.net.UidRangeParcel", classLoader);
    }

    public static ClassLoader getHostClassLoader() {
        return instance;
    }

    public static Object getNetdService() {
        return mNetdService;
    }

    public static void restoreState(ClassLoader classLoader, Object netdService) {
        instance = classLoader;
        mNetdService = netdService;
        if (Build.VERSION.SDK_INT > 35 || classLoader == null) {
            UidRangeParcel = null;
            return;
        }
        UidRangeParcel = CakeReflection.findClass("android.net.UidRangeParcel", classLoader);
    }

    private static void socketDestroyLegacy(AppRecord appRecord) {
        Object uidRangeParcels = Array.newInstance(UidRangeParcel, 1);
        int uid = appRecord.getUid();
        Array.set(uidRangeParcels, 0, CakeReflection.newInstance(UidRangeParcel, uid, uid));
        CakeReflection.callMethod(mNetdService, "socketDestroy", uidRangeParcels, new int[0]);
        Log.d(appRecord.getPackageNameWithUser() + " 断开网络连接（socketDestroy）");
    }

    private static void destroyLiveTcpSockets(AppRecord appRecord) {
        Set<Integer> uids = new HashSet<>();
        Class<?> inetDiagCls = CakeReflection.findClassIfExists("com.android.net.module.util.netlink.InetDiagMessage",
                instance);
        int uid = appRecord.getUid();
        if (inetDiagCls != null) {
            uids.add(uid);
            CakeReflection.callStaticMethod(inetDiagCls, "destroyLiveTcpSocketsByOwnerUids", uids);
        }
        Log.d(appRecord.getPackageNameWithUser() + " 断开网络连接（destroyLiveTcpSockets）");
    }

    public static void socketDestroy(AppRecord appRecord) {
        boolean networkMessageAllowed = AppConfigs.isNetworkMessageAllowed(
            appRecord.getPackageName(),
            appRecord.getUserId()
        );
        if (networkMessageAllowed) {
            Log.d(appRecord.getPackageNameWithUser() + "保持连接");
            return;
        }      
        if (Build.VERSION.SDK_INT > 35) {
            destroyLiveTcpSockets(appRecord);
            return;
        }
        socketDestroyLegacy(appRecord);
    }
}
