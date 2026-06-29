package nep.timeline.cirno.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import nep.timeline.cirno.BuildConfig;
import nep.timeline.cirno.provide.StatusBinderFacade;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.utils.SystemChecker;

public final class StatusBinderHub {
    public static final String SIGNAL_HOOK_TYPE = "hook_type";
    private static final Map<String, String> SIGNALS = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    private StatusBinderHub() {
    }

    public static final StatusBinderFacade statusBinder = new StatusBinderFacade() {
        @Override
        public String getSignal(String key) {
            return StatusBinderHub.getSignal(key);
        }

        @Override
        public String getStatusSnapshot() {
            JsonObject obj = new JsonObject();
            obj.addProperty("error", StatusBinderHub.getSignal("error"));

            ClassLoader hostClassLoader = CakeHooker.getHostClassLoader();
            boolean isXiaomi = SystemChecker.isXiaomi(hostClassLoader);
            boolean isOplus = !isXiaomi && SystemChecker.isOplus(hostClassLoader);
            obj.addProperty("device_type", isXiaomi ? "xiaomi" : isOplus ? "oplus" : "other");

            obj.addProperty("hook_type", StatusBinderHub.getSignal("hook_type"));

            JsonArray hookTypes = new JsonArray();
            if ("1".equals(StatusBinderHub.getSignal("available_millet"))) hookTypes.add("Millet");
            if ("1".equals(StatusBinderHub.getSignal("available_hans"))) hookTypes.add("Hans");
            if ("1".equals(StatusBinderHub.getSignal("available_rekernel"))) hookTypes.add("ReKernel");
            if ("1".equals(StatusBinderHub.getSignal("available_nkbinder"))) hookTypes.add("nkBinder");
            obj.add("available_hook_types", hookTypes);

            obj.addProperty("hook_version", BuildConfig.VERSION_NAME);
            obj.addProperty("packet_available", isPacketAvailable());

            return gson.toJson(obj);
        }

        @Override
        public boolean isPacketAvailable() {
            if (nep.timeline.cirno.rekernel.ReKernel.isRunning()) {
                return true;
            }
            return SystemChecker.isOplus(CakeHooker.getHostClassLoader()) || GreezeManagerServiceWrapper.instance != null;
        }

        @Override
        public String getHookVersion() {
            return BuildConfig.VERSION_NAME;
        }
    };

    public static void signalError() {
        SIGNALS.put("error", "1");
    }

    public static boolean setSignal(String key, String value) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        SIGNALS.put(key, value == null ? "" : value);
        return true;
    }

    public static String getSignal(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String value = SIGNALS.get(key);
        return value == null ? "" : value;
    }
}
