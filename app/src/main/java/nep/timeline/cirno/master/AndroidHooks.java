package nep.timeline.cirno.master;

import android.os.Build;
import android.os.FileObserver;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.ConfigFileObserver;
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
import nep.timeline.cirno.hooks.android.credentials.CredentialManagerServiceImplHook;
import nep.timeline.cirno.hooks.android.credentials.CredentialRequestSessionFinishHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeHookManager;
import nep.timeline.cirno.hooks.android.optimizer.CacheEnableFreezerHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheOnOomAdjustChangedHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheUseCompactionHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheUseFreezerHook;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.CompactionService;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.NetworkSpeedMonitor;
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
import nep.timeline.cirno.hooks.android.oom.ProcessListOomAdjHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheMemCompactionHandlerHook;
import nep.timeline.cirno.hooks.android.process.ProcessAddHook;
import nep.timeline.cirno.hooks.android.process.ProcessRemoveHook;
import nep.timeline.cirno.hooks.android.recorder.RecorderEventHook;
import nep.timeline.cirno.hooks.android.recorder.ReleaseRecorderHook;
import nep.timeline.cirno.hooks.android.signal.SendSignalHook;
import nep.timeline.cirno.hooks.android.signal.SendSignalQuietHook;
import nep.timeline.cirno.hooks.android.vpn.VpnStateHook;
import nep.timeline.cirno.hooks.android.wakelock.WakeLockHook;

public class AndroidHooks {
    private static final String CACHED_APP_OPTIMIZER_CLASS = "com.android.server.am.CachedAppOptimizer";
    private static ClassLoader sClassLoader;
    private static ConfigFileObserver sFileObserver;
    private static CacheUseCompactionHook sCacheUseCompactionHook;
    private static CacheOnOomAdjustChangedHook sCacheOnOomAdjustChangedHook;
    private static Boolean sDefaultUseCompactionValue;

    public static void start(ClassLoader classLoader) {
        // Config
        sFileObserver = new ConfigFileObserver();
        sFileObserver.startWatching();

        // ANR
        new ANRHook(classLoader);
        new ANRErrorStateHook(classLoader);
        new ANRHelperHooks(classLoader);
        // Signal
        new SendSignalHook(classLoader);
        new SendSignalQuietHook(classLoader);
        // Audio
        new AudioStateHook(classLoader);
        new PlayerBanHook(classLoader);
        new SendMediaButtonHook(classLoader);
        // Location
        new ListenerRegisterHook(classLoader);
        new ListenerUnregisterHook(classLoader);
        // InputMethod
        new InputMethodManagerService(classLoader);
        // Autofill
        new AutofillManagerServiceImplHook(classLoader);
        new AutofillSessionRemoveHook(classLoader);
        // Credential
        new CredentialManagerServiceImplHook(classLoader);
        new CredentialRequestSessionFinishHook(classLoader);
        // Network
        new NetworkManagerHook(classLoader);
        // Alarms
        new AlarmManagerService(classLoader);
        // Broadcast
        new BroadcastIntentHook(classLoader);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM)
            new BroadcastDeliveryHook(classLoader);
        else
            new BroadcastSkipHook(classLoader);
        new AutostartBlockHook(classLoader);
        // WakeLock
        new WakeLockHook(classLoader);
        // Activity
        new ActivityManagerServiceHook(classLoader);
        new ActivityManagerSystemReadyHook(classLoader);
        new ActivityStatsHook(classLoader);
        // Process
        new ProcessAddHook(classLoader);
        new ProcessRemoveHook(classLoader);
        OomAdjService.init(classLoader);
        new ProcessListOomAdjHook(classLoader);
        // Optimizer
        new CacheEnableFreezerHook(classLoader);
        new CacheUseFreezerHook(classLoader);
        new CacheMemCompactionHandlerHook(classLoader);
        sClassLoader = classLoader;
        syncCachedAppOptimizerHooks();

        // Freeze hooks (Millet/Hans/ReKernel/NkBinder)
        new FreezeHookManager(classLoader).start(classLoader);

        // Recorder
        new RecorderEventHook(classLoader);
        new ReleaseRecorderHook(classLoader);
        // Camera
        new CameraStateHook(classLoader);
        new CameraBinderDiedHook(classLoader);
        // Vpn
        new VpnStateHook(classLoader);
        // Intent
        new PendingIntentHook(classLoader);
        // Notification
        new NotificationHook(classLoader);

        // Compaction enums
        CompactionService.initEnums(classLoader);
    }

    public static void syncCachedAppOptimizerHooks() {
        ClassLoader classLoader = sClassLoader;
        if (classLoader == null)
            return;

        if (isCompactionEnabled()) {
            if (sCacheUseCompactionHook == null) {
                sCacheUseCompactionHook = new CacheUseCompactionHook(classLoader);
            } else if (!sCacheUseCompactionHook.isHooked()) {
                sCacheUseCompactionHook.startHook();
            }

            if (sCacheOnOomAdjustChangedHook == null) {
                sCacheOnOomAdjustChangedHook = new CacheOnOomAdjustChangedHook(classLoader);
            } else if (!sCacheOnOomAdjustChangedHook.isHooked()) {
                sCacheOnOomAdjustChangedHook.startHook();
            }
            
            return;
        }

        if (sCacheUseCompactionHook != null) {
            sCacheUseCompactionHook.unhook();
        }
        if (sCacheOnOomAdjustChangedHook != null) {
            sCacheOnOomAdjustChangedHook.unhook();
        }
    }

    private static boolean isCompactionEnabled() {
        return GlobalVars.globalSettings != null && GlobalVars.globalSettings.compactionEnabled;
    }

    public static void stopForHotReload() {
        try {
            if (sFileObserver != null) {
                sFileObserver.stopWatching();
                sFileObserver = null;
            }
        } catch (Throwable ignored) {
        }
        NetworkSpeedMonitor.stopForHotReload();
        MonitorBinderHub.stopForHotReload();
        NkBinderService.stop();
        ReKernel.stop();
        Handlers.shutdownForHotReload();
    }

}
