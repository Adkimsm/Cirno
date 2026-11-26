package nep.timeline.cirno.master;

import android.os.Build;

import nep.timeline.cirno.configs.ConfigFileObserver;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.NotificationExemptionHandler;
import nep.timeline.cirno.hooks.android.activity.ActivityManagerServiceHook;
import nep.timeline.cirno.hooks.android.activity.ActivityManagerSystemReadyHook;
import nep.timeline.cirno.hooks.android.activity.ActivityStatsHook;
import nep.timeline.cirno.hooks.android.alarms.AlarmManagerService;
import nep.timeline.cirno.hooks.android.anr.ANRErrorStateHook;
import nep.timeline.cirno.hooks.android.anr.ANRHelperHooks;
import nep.timeline.cirno.hooks.android.anr.ANRHook;
import nep.timeline.cirno.hooks.android.audio.AudioStateHook;
import nep.timeline.cirno.hooks.android.audio.PlayerBanHook;
import nep.timeline.cirno.hooks.android.audio.SendMediaButtonHook;
import nep.timeline.cirno.hooks.android.autofill.AutofillManagerServiceImplHook;
import nep.timeline.cirno.hooks.android.autofill.AutofillSessionRemoveHook;
import nep.timeline.cirno.hooks.android.camera.CameraBinderDiedHook;
import nep.timeline.cirno.hooks.android.camera.CameraStateHook;
import nep.timeline.cirno.hooks.android.window.WindowAddHook;
import nep.timeline.cirno.hooks.android.window.WindowAppOpVisibilityHook;
import nep.timeline.cirno.hooks.android.window.WindowRemoveHook;
import nep.timeline.cirno.hooks.android.window.WindowViewVisibilityHook;
import nep.timeline.cirno.hooks.android.window.WindowAppOpStateInitHook;
import nep.timeline.cirno.hooks.android.credentials.CredentialManagerServiceImplHook;
import nep.timeline.cirno.hooks.android.credentials.CredentialRequestSessionFinishHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeHookManager;
import nep.timeline.cirno.hooks.android.optimizer.CacheEnableFreezerHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheUseFreezerHook;
import nep.timeline.cirno.hooks.android.provider.ContentProviderAcquireHook;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.NetworkSpeedMonitor;
import nep.timeline.cirno.services.NetworkStateMonitor;
import nep.timeline.cirno.services.NkBinderService;
import nep.timeline.cirno.services.OomAdjService;
import nep.timeline.cirno.rekernel.ReKernel;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.hooks.android.broadcast.AutostartBlockHook;
import nep.timeline.cirno.hooks.android.broadcast.BroadcastDeliveryHook;
import nep.timeline.cirno.hooks.android.broadcast.BroadcastIntentHook;
import nep.timeline.cirno.hooks.android.broadcast.BroadcastSkipHook;
import nep.timeline.cirno.hooks.android.network.NetworkManagerHook;
import nep.timeline.cirno.hooks.android.input.InputMethodManagerService;
import nep.timeline.cirno.hooks.android.intent.PendingIntentHook;
import nep.timeline.cirno.hooks.android.location.ListenerRegisterHook;
import nep.timeline.cirno.hooks.android.location.ListenerUnregisterHook;
import nep.timeline.cirno.hooks.android.notification.NotificationHook;
import nep.timeline.cirno.hooks.android.notification.NotificationCancellationHook;
import nep.timeline.cirno.hooks.android.notification.NotificationSystemUpdateHook;
import nep.timeline.cirno.hooks.android.notification.registerDismissedByUser;
import nep.timeline.cirno.hooks.android.notification.registerPostedByAppHook;
import nep.timeline.cirno.hooks.android.notification.registerRemovedByAppHook;
import nep.timeline.cirno.hooks.android.notification.registerUpdatedByAppHook;
import nep.timeline.cirno.hooks.android.oom.ProcessListOomAdjHook;
import nep.timeline.cirno.hooks.android.process.ProcessAddHook;
import nep.timeline.cirno.hooks.android.process.ProcessRemoveHook;
import nep.timeline.cirno.hooks.android.recorder.RecorderEventHook;
import nep.timeline.cirno.hooks.android.recorder.ReleaseRecorderHook;
import nep.timeline.cirno.hooks.android.signal.SendSignalHook;
import nep.timeline.cirno.hooks.android.signal.SendSignalQuietHook;
import nep.timeline.cirno.hooks.android.vpn.VpnStateHook;
import nep.timeline.cirno.hooks.android.wakelock.WakeLockHook;
import nep.timeline.cirno.hooks.android.battery.DeviceIdleStartHook;
import nep.timeline.cirno.hooks.android.battery.DeviceIdleBootPhaseHook;
import nep.timeline.cirno.hooks.android.battery.DeviceIdleWhitelistUpdateHook;
import nep.timeline.cirno.log.Log;

public class AndroidHooks {
    private static ConfigFileObserver sFileObserver;

    public static void start(ClassLoader classLoader) {
        // Config
        sFileObserver = new ConfigFileObserver();
        sFileObserver.startWatching();

        // ANR
        new ANRHook(classLoader).install();
        new ANRErrorStateHook(classLoader).install();
        new ANRHelperHooks(classLoader).install();
        // Signal
        new SendSignalHook(classLoader).install();
        new SendSignalQuietHook(classLoader).install();
        // Audio
        new AudioStateHook(classLoader).install();
        new PlayerBanHook(classLoader).install();
        new SendMediaButtonHook(classLoader).install();
        // Location
        new ListenerRegisterHook(classLoader).install();
        new ListenerUnregisterHook(classLoader).install();
        // InputMethod
        new InputMethodManagerService(classLoader).install();
        // Autofill
        new AutofillManagerServiceImplHook(classLoader).install();
        new AutofillSessionRemoveHook(classLoader).install();
        // Credential
        new CredentialManagerServiceImplHook(classLoader).install();
        new CredentialRequestSessionFinishHook(classLoader).install();
        // Network
        new NetworkManagerHook(classLoader).install();
        // Alarms
        new AlarmManagerService(classLoader).install();
        // Broadcast
        new BroadcastIntentHook(classLoader).install();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
            new BroadcastDeliveryHook(classLoader).install();
        else
            new BroadcastSkipHook(classLoader).install();
        new AutostartBlockHook(classLoader).install();
        // WakeLock
        new WakeLockHook(classLoader).install();
        new DeviceIdleStartHook(classLoader).install();
        new DeviceIdleBootPhaseHook(classLoader).install();
        new DeviceIdleWhitelistUpdateHook(classLoader).install();
        // Activity
        new ActivityManagerServiceHook(classLoader).install();
        new ActivityManagerSystemReadyHook(classLoader).install();
        new ActivityStatsHook(classLoader).install();
        // ContentProvider
        new ContentProviderAcquireHook(classLoader).install();
        // Process
        new ProcessAddHook(classLoader).install();
        new ProcessRemoveHook(classLoader).install();
        OomAdjService.init(classLoader);
        new ProcessListOomAdjHook(classLoader).install();
        // Optimizer
        new CacheEnableFreezerHook(classLoader).install();
        new CacheUseFreezerHook(classLoader).install();

        // Freeze hooks (Millet/Hans/ReKernel/NkBinder)
        new FreezeHookManager(classLoader).start(classLoader);

        // Recorder
        new RecorderEventHook(classLoader).install();
        new ReleaseRecorderHook(classLoader).install();
        // Camera
        new CameraStateHook(classLoader).install();
        new CameraBinderDiedHook(classLoader).install();
        // Overlay
        new WindowAddHook(classLoader).install();
        new WindowRemoveHook(classLoader).install();
        new WindowAppOpVisibilityHook(classLoader).install();
        new WindowAppOpStateInitHook(classLoader).install();
        new WindowViewVisibilityHook(classLoader).install();
        // Vpn
        new VpnStateHook(classLoader).install();
        // Intent
        new PendingIntentHook(classLoader).install();
        // Notification
        new NotificationHook(classLoader).install();
        MethodHook posted = new registerPostedByAppHook(classLoader);
        MethodHook updated = new registerUpdatedByAppHook(classLoader);
        MethodHook cancelled = new NotificationCancellationHook(classLoader);
        MethodHook systemUpdated = new NotificationSystemUpdateHook(classLoader);
        posted.install();
        updated.install();
        new registerDismissedByUser(classLoader).install();
        new registerRemovedByAppHook(classLoader).install();
        cancelled.install();
        systemUpdated.install();
        if (posted.isHooked() && updated.isHooked() && cancelled.isHooked() && systemUpdated.isHooked()) {
            NotificationExemptionHandler.configure(classLoader);
        } else {
            Log.w("通知生命周期 Hook 不完整，停用常驻通知冻结豁免");
        }
    }

    public static void stopForHotReload() {
        try {
            if (sFileObserver != null) {
                sFileObserver.stopWatching();
                sFileObserver = null;
            }
        } catch (Throwable ignored) {
        }
        NotificationExemptionHandler.stopForHotReload();
        NetworkStateMonitor.stopForHotReload();
        NetworkSpeedMonitor.stopForHotReload();
        MonitorBinderHub.stopForHotReload();
        NkBinderService.stop();
        ReKernel.stop();
        Handlers.shutdownForHotReload();
    }

}
