package nep.timeline.cirno.services;

import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;

public class VivoFreezeNetCtrlWrapper {
    private static volatile Object freezeNetCtrl = null;

    public static void setInstance(Object instance) {
        freezeNetCtrl = instance;
    }

    public static Object getInstance() {
        return freezeNetCtrl;
    }

    /**
     * 启动网络追踪（应用冻结时调用）
     * 类似小米的 monitorNet
     */
    public static void startTrackerUid(int uid) {
        if (freezeNetCtrl == null)
            return;
        try {
            CakeReflection.callMethod(freezeNetCtrl, "startTrackerUid", uid);
            Log.d(uid + " startTrackerUid (vivo FreezeNetCtrl)");
        } catch (Throwable throwable) {
            Log.e("startTrackerUid", throwable);
        }
    }

    /**
     * 停止网络追踪（应用解冻时调用）
     * 类似小米的 clearMonitorNet
     */
    public static void stopTrackerUid(int uid) {
        if (freezeNetCtrl == null)
            return;
        try {
            CakeReflection.callMethod(freezeNetCtrl, "stopTrackerUid", uid);
            Log.d(uid + " stopTrackerUid (vivo FreezeNetCtrl)");
        } catch (Throwable throwable) {
            Log.e("stopTrackerUid", throwable);
        }
    }

    /**
     * 设置 UID 冻结状态（可选，额外通知 vivo 框架）
     */
    public static void setUidFreezeState(int uid, boolean frozen) {
        if (freezeNetCtrl == null)
            return;
        try {
            CakeReflection.callMethod(freezeNetCtrl, "setUidFreezeState", uid, frozen);
            Log.d(uid + " setUidFreezeState: " + frozen + " (vivo FreezeNetCtrl)");
        } catch (Throwable throwable) {
            Log.e("setUidFreezeState", throwable);
        }
    }
}
