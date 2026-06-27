package nep.timeline.cirno.master;

import android.os.Build;
import android.os.FileObserver;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.ConfigFileObserver;
import nep.timeline.cirno.configs.settings.GlobalSettings;
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
import nep.timeline.cirno.hooks.android.binder.HansKernelUnfreezeHook;
import nep.timeline.cirno.hooks.android.binder.MilletBinderTransHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheEnableFreezerHook;
import nep.timeline.cirno.hooks.android.optimizer.CacheUseFreezerHook;
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
import nep.timeline.cirno.hooks.android.process.ProcessAddHook;
import nep.timeline.cirno.hooks.android.process.ProcessRemoveHook;
import nep.timeline.cirno.hooks.android.recorder.RecorderEventHook;
import nep.timeline.cirno.hooks.android.recorder.ReleaseRecorderHook;
import nep.timeline.cirno.hooks.android.signal.SendSignalHook;
import nep.timeline.cirno.hooks.android.signal.SendSignalQuietHook;
import nep.timeline.cirno.hooks.android.vpn.VpnStateHook;
import nep.timeline.cirno.hooks.android.wakelock.WakeLockHook;
import nep.timeline.cirno.hooks.android.xiaomi.GreezeManagerServiceHook;
import nep.timeline.cirno.hooks.android.xiaomi.MilletMonitorHook;
import nep.timeline.cirno.hooks.android.xiaomi.ReportNetHook;
import nep.timeline.cirno.hooks.android.xiaomi.ReportSignalHook;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.rekernel.ReKernel;
import nep.timeline.cirno.services.NetworkManagementService;
import nep.timeline.cirno.services.NkBinderService;
import nep.timeline.cirno.services.StatusBinderHub;
import nep.timeline.cirno.utils.SystemChecker;

public class AndroidHooks {
    private static ConfigFileObserver sFileObserver;
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
        new ReportSignalHook(classLoader);
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
        // Optimizer
        new CacheEnableFreezerHook(classLoader);
        new CacheUseFreezerHook(classLoader);
        // Binder
        MethodHook hansHook = new HansKernelUnfreezeHook(classLoader);
        MethodHook milletHook = new MilletBinderTransHook(classLoader);
        new GreezeManagerServiceHook(classLoader);
        new MilletMonitorHook(classLoader);
        new ReportNetHook(classLoader);

        // Hook type availability
        StatusBinderHub.setSignal("available_millet", milletHook.isHooked() ? "1" : "0");
        StatusBinderHub.setSignal("available_hans", hansHook.isHooked() ? "1" : "0");
        StatusBinderHub.setSignal("available_rekernel", "1");
        StatusBinderHub.setSignal("available_nkbinder", NkBinderService.isAvailable() ? "1" : "0");

        // Hook type selection
        String hookType = GlobalVars.globalSettings != null
                ? GlobalVars.globalSettings.hookType : GlobalSettings.HOOK_TYPE_AUTO;

        switch (hookType) {
            case GlobalSettings.HOOK_TYPE_MILLET -> {
                if (milletHook.isHooked()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Millet");
                    if (hansHook.isHooked()) hansHook.unhook();
                }
            }
            case GlobalSettings.HOOK_TYPE_HANS -> {
                if (hansHook.isHooked()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Hans");
                    if (milletHook.isHooked()) milletHook.unhook();
                }
            }
            case GlobalSettings.HOOK_TYPE_REKERNEL -> {
                if (milletHook.isHooked()) milletHook.unhook();
                if (hansHook.isHooked()) hansHook.unhook();
                StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE, "Re-Kernel");
                ReKernel.start(classLoader);
            }
            case GlobalSettings.HOOK_TYPE_NKBINDER -> {
                if (milletHook.isHooked()) milletHook.unhook();
                if (hansHook.isHooked()) hansHook.unhook();
                NkBinderService.start(classLoader);
            }
            default -> {
                // Auto: ReKernel > Millet/Hans > nkBinder
                ReKernel.start(classLoader);
                if (milletHook.isHooked() || hansHook.isHooked()) {
                    StatusBinderHub.setSignal(StatusBinderHub.SIGNAL_HOOK_TYPE,
                            hansHook.isHooked() ? "Hans" : "Millet");
                } else if (NkBinderService.isAvailable()) {
                    NkBinderService.start(classLoader);
                }
            }
        }
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
    }

}
