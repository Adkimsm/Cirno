package nep.timeline.cirno.binder;

import android.os.Binder;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.provide.ApplicationBinderFacade;
import nep.timeline.cirno.provide.FrozenStateBinderFacade;
import nep.timeline.cirno.provide.BatteryOptimizationBinderFacade;
import nep.timeline.cirno.services.BatteryOptimizationService;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.StatusBinderHub;

public final class CirnoBinderService {
    private static final String MANAGER_PACKAGE = "nep.timeline.cirno";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_FROZEN_STATES = "frozenStates";

    private static final ICirnoService.Stub SERVICE = new ICirnoService.Stub() {
        @Override
        public String getSignal(String key) {
            enforceUiCaller();
            try {
                return StatusBinderHub.getSignal(key);
            } catch (Throwable e) {
                Log.w("CirnoBinderService getSignal failed", e);
                return "";
            }
        }

        @Override
        public String getStatusSnapshot() {
            enforceUiCaller();
            try {
                return StatusBinderHub.statusBinder.getStatusSnapshot();
            } catch (Throwable e) {
                Log.w("CirnoBinderService getStatusSnapshot failed", e);
                return null;
            }
        }

        @Override
        public boolean isPacketAvailable() {
            enforceUiCaller();
            try {
                return StatusBinderHub.statusBinder.isPacketAvailable();
            } catch (Throwable e) {
                Log.w("CirnoBinderService isPacketAvailable failed", e);
                return false;
            }
        }

        @Override
        public String getHookVersion() {
            enforceUiCaller();
            try {
                return StatusBinderHub.statusBinder.getHookVersion();
            } catch (Throwable e) {
                Log.w("CirnoBinderService getHookVersion failed", e);
                return null;
            }
        }

        @Override
        public List<String> getRunningApplication() {
            enforceUiCaller();
            try {
                return new ArrayList<>(MonitorBinderHub.getApplicationBinderFacade().getRunningApplication());
            } catch (Throwable e) {
                Log.w("CirnoBinderService getRunningApplication failed", e);
                return Collections.emptyList();
            }
        }

        @Override
        public String getProcessesForApp(String packageName, int userId) {
            enforceUiCaller();
            try {
                return MonitorBinderHub.getApplicationBinderFacade().getProcessesForApp(packageName, userId);
            } catch (Throwable e) {
                Log.w("CirnoBinderService getProcessesForApp failed", e);
                return "[]";
            }
        }

        @Override
        public String getRunningProcessesForApp(String packageName, int userId) {
            enforceUiCaller();
            try {
                return MonitorBinderHub.getApplicationBinderFacade().getRunningProcessesForApp(packageName, userId);
            } catch (Throwable e) {
                Log.w("CirnoBinderService getRunningProcessesForApp failed", e);
                return "{}";
            }
        }

        @Override
        public String getNetworkSpeed(String packageName, int userId) {
            enforceUiCaller();
            try {
                return MonitorBinderHub.getApplicationBinderFacade().getNetworkSpeed(packageName, userId);
            } catch (Throwable e) {
                Log.w("CirnoBinderService getNetworkSpeed failed", e);
                return "{\"rx\":0,\"tx\":0}";
            }
        }

        @Override
        public String isFrozen(String packageName, int userId) {
            enforceUiCaller();
            try {
                return MonitorBinderHub.getFrozenStateBinderFacade().isFrozen(packageName, userId);
            } catch (Throwable e) {
                Log.w("CirnoBinderService isFrozen failed", e);
                return "NOT_FROZEN[UNKNOWN]";
            }
        }

        @Override
        public List<String> getFrozenStates(List<String> apps) {
            enforceUiCaller();
            try {
                return new ArrayList<>(MonitorBinderHub.getFrozenStateBinderFacade().getFrozenStates(apps));
            } catch (Throwable e) {
                Log.w("CirnoBinderService getFrozenStates failed", e);
                return Collections.emptyList();
            }
        }

        @Override
        public Bundle getMonitorSnapshot() {
            enforceUiCaller();
            Bundle bundle = new Bundle();
            try {
                ApplicationBinderFacade applicationBinder = MonitorBinderHub.getApplicationBinderFacade();
                FrozenStateBinderFacade frozenStateBinder = MonitorBinderHub.getFrozenStateBinderFacade();
                List<String> running = new ArrayList<>(applicationBinder.getRunningApplication());
                List<String> frozenStates = new ArrayList<>(frozenStateBinder.getFrozenStates(new ArrayList<>(running)));
                bundle.putStringArrayList(KEY_RUNNING, new ArrayList<>(running));
                bundle.putStringArrayList(KEY_FROZEN_STATES, new ArrayList<>(frozenStates));
            } catch (Throwable e) {
                Log.w("CirnoBinderService getMonitorSnapshot failed", e);
                bundle.putStringArrayList(KEY_RUNNING, new ArrayList<>());
                bundle.putStringArrayList(KEY_FROZEN_STATES, new ArrayList<>());
            }
            return bundle;
        }

        @Override
        public boolean isBatteryOptimizationEnabled(String packageName, int userId) {
            enforceUiCaller();
            long identity = Binder.clearCallingIdentity();
            try {
                return BatteryOptimizationService.isBatteryOptimizationEnabled(packageName, userId);
            } catch (Throwable e) {
                Log.w("CirnoBinderService isBatteryOptimizationEnabled failed", e);
                return true;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public boolean setBatteryOptimizationEnabled(String packageName, int userId, boolean enabled) {
            enforceUiCaller();
            long identity = Binder.clearCallingIdentity();
            try {
                return BatteryOptimizationService.setBatteryOptimizationEnabled(packageName, userId, enabled);
            } catch (Throwable e) {
                Log.w("CirnoBinderService setBatteryOptimizationEnabled failed", e);
                return false;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }

        @Override
        public boolean syncBatteryOptimizationWhitelist() {
            enforceUiCaller();
            long identity = Binder.clearCallingIdentity();
            try {
                return BatteryOptimizationService.sync();
            } catch (Throwable e) {
                Log.w("CirnoBinderService syncBatteryOptimizationWhitelist failed", e);
                return false;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    };

    private CirnoBinderService() {
    }

    public static ICirnoService getService() {
        return SERVICE;
    }

    private static void enforceUiCaller() {
        int uid = Binder.getCallingUid();
        String[] packages = ActivityManagerService.getPackagesForUid(uid);
        if (packages == null) {
            throw new SecurityException("unauthorized caller uid=" + uid);
        }
        for (String packageName : packages) {
            if (MANAGER_PACKAGE.equals(packageName)) {
                return;
            }
        }
        throw new SecurityException("unauthorized caller uid=" + uid);
    }
}
