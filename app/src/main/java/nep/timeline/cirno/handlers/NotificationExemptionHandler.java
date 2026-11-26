package nep.timeline.cirno.handlers;

import android.service.notification.StatusBarNotification;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.configs.policy.PolicyKey;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.threads.Handlers;

public final class NotificationExemptionHandler {
    private static final AtomicBoolean REFRESH_QUEUED = new AtomicBoolean();
    private static volatile ClassLoader serviceClassLoader;
    private static volatile boolean ready;
    private static volatile Set<String> exemptApps = Set.of();
    // Accessed only on Handlers.notification.
    private static Object notificationService;
    private static boolean snapshotFailed;

    private NotificationExemptionHandler() {
    }

    public static void configure(ClassLoader classLoader) {
        serviceClassLoader = classLoader;
    }

    public static void onSystemReady() {
        ready = serviceClassLoader != null;
        requestRefresh();
    }

    public static void stopForHotReload() {
        ready = false;
        Handlers.notification.removeCallbacks(REFRESH);
    }

    public static boolean isExempt(String packageName, int userId) {
        return ready && exemptApps.contains(PolicyKey.of(packageName, userId))
                && AppConfigs.isOngoingNotificationAllowed(packageName, userId)
                && !AppConfigs.isBlackApp(packageName, userId);
    }

    public static void onNotificationChanged(Object record) {
        if (!ready || record == null || !AppConfigs.hasAnyOngoingNotificationApps()) {
            return;
        }
        try {
            if (CakeReflection.callMethod(record, "getSbn") instanceof StatusBarNotification sbn
                    && AppConfigs.isOngoingNotificationAllowed(sbn.getPackageName(), sbn.getUserId())) {
                requestRefresh();
            }
        } catch (Throwable ignored) {
            // Verify the active list if a vendor record cannot be read in the event callback.
            requestRefresh();
        }
    }

    public static void requestRefresh() {
        if (ready && REFRESH_QUEUED.compareAndSet(false, true) && !Handlers.notification.post(REFRESH)) {
            REFRESH_QUEUED.set(false);
        }
    }

    private static final Runnable REFRESH = () -> {
        REFRESH_QUEUED.set(false);
        if (ready) {
            refresh();
        }
    };

    private static void refresh() {
        Set<String> current = Set.of();
        if (AppConfigs.hasAnyOngoingNotificationApps()) {
            try {
                current = readExemptApps();
                snapshotFailed = false;
            } catch (Throwable error) {
                notificationService = null;
                if (!snapshotFailed) {
                    Log.w("无法读取活跃通知，暂停常驻通知冻结豁免", error);
                    snapshotFailed = true;
                }
            }
        }
        if (!ready) {
            return;
        }
        Set<String> previous = exemptApps;
        exemptApps = current;
        if (previous.equals(current)) {
            return;
        }
        for (AppRecord record : AppService.getAllRecordsSnapshot()) {
            String key = PolicyKey.of(record.getPackageName(), record.getUserId());
            boolean exempt = current.contains(key);
            if (exempt != previous.contains(key)) {
                FreezerHandler.handler.post(() -> onExemptionChanged(record, exempt));
            }
        }
    }

    private static Set<String> readExemptApps() {
        if (notificationService == null) {
            Class<?> localServices = CakeReflection.findClass("com.android.server.LocalServices", serviceClassLoader);
            Class<?> internal = CakeReflection.findClass(
                    "com.android.server.notification.NotificationManagerInternal", serviceClassLoader);
            Object manager = CakeReflection.callStaticMethod(localServices, "getService",
                    new Class<?>[] {Class.class}, internal);
            if (manager == null) {
                throw new IllegalStateException("NotificationManagerInternal is not ready");
            }
            notificationService = CakeReflection.getSurroundingThis(manager);
        }

        Set<String> result = new HashSet<>();
        Object lock = CakeReflection.getObjectField(notificationService, "mNotificationLock");
        synchronized (lock) {
            List<?> active = (List<?>) CakeReflection.getObjectField(notificationService, "mNotificationList");
            // Rebuild from the active list, so removing one of several ongoing notifications
            // only ends the exemption once no matching notification remains.
            for (Object record : active) {
                StatusBarNotification sbn = (StatusBarNotification) CakeReflection.callMethod(record, "getSbn");
                if (sbn == null || !sbn.isOngoing()) {
                    continue;
                }
                int userId = sbn.getUserId();
                String packageName = sbn.getPackageName();
                if (userId >= 0 && AppConfigs.isOngoingNotificationAllowed(packageName, userId)
                        && !AppConfigs.isBlackApp(packageName, userId)) {
                    result.add(PolicyKey.of(packageName, userId));
                }
            }
        }
        return Set.copyOf(result);
    }

    private static void onExemptionChanged(AppRecord record, boolean exempt) {
        String packageName = record.getPackageName();
        int userId = record.getUserId();
        if (!ready || AppService.get(packageName, userId) != record) {
            return;
        }
        if (isExempt(packageName, userId) != exempt) {
            return;
        }
        if (exempt) {
            // thaw() also clears timers. Only do that for an app Cirno actually froze.
            if (record.isFrozen()) {
                FreezerService.thaw(record);
            }
        } else {
            FreezerHandler.sendFreezeMessage(record);
        }
    }
}
