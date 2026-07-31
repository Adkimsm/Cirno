package nep.timeline.cirno.services;

import android.os.IBinder;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.entity.AppState;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.threads.Handlers;

public class NetworkSpeedMonitor {
    private static final Object READ_METHOD_LOCK = new Object();
    private static volatile IBinder sNetStatsBinder;
    private static volatile boolean sMonitoring = false;
    private static volatile Method sReadNetworkStatsUidDetailMethod;

    private static final ConcurrentHashMap<Integer, long[]> sSnapshots = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, long[]> sSpeedCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Boolean> sReadFailed = new ConcurrentHashMap<>();

    public static void init() {
        if (sMonitoring)
            return;
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getDeclaredMethod("getService", String.class);
            sNetStatsBinder = (IBinder) getService.invoke(null, "netstats");
        } catch (Throwable e) {
            Log.e("NetworkSpeedMonitor: failed to get netstats service", e);
        }

        if (sNetStatsBinder != null) {
            Log.i("NetworkSpeedMonitor: initialized");
        } else {
            Log.e("NetworkSpeedMonitor: netstats service not available");
            return;
        }

        sMonitoring = true;
        Handlers.network.postDelayed(NetworkSpeedMonitor::poll, 1000);
    }

    public static void stopForHotReload() {
        sMonitoring = false;
        Handlers.network.removeCallbacksAndMessages(null);
        sSnapshots.clear();
        sSpeedCache.clear();
        sReadFailed.clear();
        sNetStatsBinder = null;
        sReadNetworkStatsUidDetailMethod = null;
    }

    private static final long POLL_INTERVAL_ACTIVE_MS = 1000L;
    private static final long POLL_INTERVAL_IDLE_MS = 5000L;

    private static void poll() {
        if (!sMonitoring) {
            return;
        }
        long nextDelay = POLL_INTERVAL_ACTIVE_MS;
        try {
            // 省电快路径：没有任何应用开启网速监控时，不再每秒扫描全部 AppRecord 并读取网络统计
            if (!AppConfigs.hasAnyNetworkSpeedApps()) {
                if (!sSnapshots.isEmpty() || !sSpeedCache.isEmpty()) {
                    sSnapshots.clear();
                    sSpeedCache.clear();
                    sReadFailed.clear();
                }
                nextDelay = POLL_INTERVAL_IDLE_MS;
            } else {
                long now = System.currentTimeMillis();
                int threshold = GlobalVars.globalSettings != null ? GlobalVars.globalSettings.networkSpeedThreshold : 102400;
                java.util.HashSet<Integer> handledUids = new java.util.HashSet<>();
                for (AppRecord record : AppService.getAllRecordsSnapshot()) {
                    if (record == null)
                        continue;
                    if (!AppConfigs.isNetworkSpeedAllowed(record.getPackageName(), record.getUserId())) {
                        sReadFailed.remove(record.getUid());
                        sSnapshots.remove(record.getUid());
                        sSpeedCache.remove(record.getUid());
                        record.getAppState().setNetworkActive(false);
                        continue;
                    }
                    // 已冻结的应用无需监控网速（冻结时不产生主动流量），丢弃快照避免解冻后误判
                    if (record.isFrozen()) {
                        sSnapshots.remove(record.getUid());
                        continue;
                    }
                    // 共享 uid 的多个应用只读一次内核统计
                    if (!handledUids.add(record.getUid()))
                        continue;
                    readAndCalculate(record, now, threshold);
                }
            }
        } catch (Throwable e) {
            Log.e("NetworkSpeedMonitor poll error", e);
        }
        if (sMonitoring) {
            Handlers.network.postDelayed(NetworkSpeedMonitor::poll, nextDelay);
        }
    }

    private static void readAndCalculate(AppRecord appRecord, long now, int threshold) {
        int uid = appRecord.getUid();
        AppState appState = appRecord.getAppState();
        try {
            Method readMethod = sReadNetworkStatsUidDetailMethod;
            if (readMethod == null) {
                synchronized (READ_METHOD_LOCK) {
                    readMethod = sReadNetworkStatsUidDetailMethod;
                    if (readMethod == null) {
                        Class<?> serviceClass = sNetStatsBinder.getClass();
                        readMethod = serviceClass.getDeclaredMethod("readNetworkStatsUidDetail",
                                int.class, String[].class, int.class);
                        readMethod.setAccessible(true);
                        sReadNetworkStatsUidDetailMethod = readMethod;
                    }
                }
            }
            Object stats = readMethod.invoke(sNetStatsBinder, uid, null, -1);

            long totalRx = 0;
            long totalTx = 0;

            int size = (int) CakeReflection.callMethod(stats, "size");
            Object entry = null;
            for (int i = 0; i < size; i++) {
                entry = CakeReflection.callMethod(stats, "getValues", i, entry);
                totalRx += (long) CakeReflection.getObjectField(entry, "rxBytes");
                totalTx += (long) CakeReflection.getObjectField(entry, "txBytes");
            }

            long[] prev = sSnapshots.get(uid);
            long rxSpeed = 0;
            long txSpeed = 0;
            boolean active = false;
            if (prev != null) {
                long deltaTime = now - prev[2];
                if (deltaTime > 0) {
                    rxSpeed = (totalRx - (long) prev[0]) * 1000 / deltaTime;
                    txSpeed = (totalTx - (long) prev[1]) * 1000 / deltaTime;
                    if (rxSpeed < 0) rxSpeed = 0;
                    if (txSpeed < 0) txSpeed = 0;
                    sSpeedCache.put(uid, new long[]{rxSpeed, txSpeed});
                    active = rxSpeed + txSpeed > threshold;
                }
            }
            sSnapshots.put(uid, new long[]{totalRx, totalTx, now});
            sReadFailed.remove(uid);
            if (appState.setNetworkActive(active) && !active) {
                // 网速降回阈值以下：豁免条件消失，补发冻结消息。
                // 否则应用会在一次"冻结时刻恰好网速活跃"后永远保持解冻（没有其它事件源再触发冻结）
                nep.timeline.cirno.threads.FreezerHandler.sendFreezeMessage(appRecord);
            }
        } catch (Throwable e) {
            if (sReadFailed.put(uid, true) == null) {
                Log.w("NetworkSpeedMonitor: 读取失败 app=" + appRecord.getPackageNameWithUser() + " uid=" + uid, e);
            }
        }
    }

    public static long[] getSpeed(int uid) {
        long[] speed = sSpeedCache.get(uid);
        return speed != null ? speed : new long[]{0, 0};
    }
}
