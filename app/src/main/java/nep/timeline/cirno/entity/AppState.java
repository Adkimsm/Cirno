package nep.timeline.cirno.entity;

import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppState {
    private final AppRecord parent;
    private final Set<IBinder> activities = new HashSet<>();
    private final Set<IBinder> locationListeners = new HashSet<>();
    private final Set<Integer> interfaceIds = new HashSet<>();
    private final Set<Integer> recodingIds = new HashSet<>();
    private final Set<String> cameraIds = new HashSet<>();
    private volatile boolean visible = false;
    private volatile boolean location = false;
    private volatile boolean audio = false;
    private volatile boolean recording = false;
    private volatile boolean camera = false;
    private volatile boolean vpn = false;
    private volatile boolean networkActive = false;
    private volatile boolean waitingNotification = false;

    public AppState(AppRecord appRecord) {
        this.parent = appRecord;
    }

    public synchronized boolean setVisible(boolean value) {
        if (visible == value)
            return false;
        visible = value;
        return true;
    }

    public synchronized boolean setLocation(boolean value) {
        if (location == value)
            return false;
        location = value;
        return true;
    }

    public synchronized boolean setAudio(boolean value) {
        if (audio == value)
            return false;
        audio = value;
        return true;
    }

    public synchronized boolean setRecording(boolean value) {
        if (recording == value)
            return false;
        recording = value;
        return true;
    }

    public synchronized boolean setVpn(boolean value) {
        if (vpn == value)
            return false;
        vpn = value;
        return true;
    }

    public synchronized boolean setNetworkActive(boolean value) {
        if (networkActive == value)
            return false;
        networkActive = value;
        return true;
    }

    public synchronized boolean setWaitingNotification(boolean value) {
        if (waitingNotification == value)
            return false;
        waitingNotification = value;
        return true;
    }

    public synchronized boolean addActivity(IBinder activity) {
        if (!activities.add(activity) || visible)
            return false;
        visible = true;
        return true;
    }

    public synchronized boolean removeActivity(IBinder activity) {
        if (!activities.remove(activity) || !activities.isEmpty() || !visible)
            return false;
        visible = false;
        return true;
    }

    public synchronized boolean addLocationListener(IBinder listener) {
        if (!locationListeners.add(listener) || location)
            return false;
        location = true;
        return true;
    }

    public synchronized boolean removeLocationListener(IBinder listener) {
        if (!locationListeners.remove(listener) || !locationListeners.isEmpty() || !location)
            return false;
        location = false;
        return true;
    }

    public synchronized boolean addAudioInterface(int interfaceId) {
        if (!interfaceIds.add(interfaceId) || audio)
            return false;
        audio = true;
        return true;
    }

    public synchronized boolean removeAudioInterface(int interfaceId) {
        if (!interfaceIds.remove(interfaceId) || !interfaceIds.isEmpty() || !audio)
            return false;
        audio = false;
        return true;
    }

    public synchronized boolean addRecordingId(int recordingId) {
        if (!recodingIds.add(recordingId) || recording)
            return false;
        recording = true;
        return true;
    }

    public synchronized boolean removeRecordingId(int recordingId) {
        if (!recodingIds.remove(recordingId) || !recodingIds.isEmpty() || !recording)
            return false;
        recording = false;
        return true;
    }

    public synchronized boolean addCameraId(String cameraId) {
        if (cameraId == null || !cameraIds.add(cameraId) || camera)
            return false;
        camera = true;
        return true;
    }

    public synchronized boolean removeCameraId(String cameraId) {
        if (cameraId == null || !cameraIds.remove(cameraId) || !cameraIds.isEmpty() || !camera)
            return false;
        camera = false;
        return true;
    }

    public synchronized boolean clearCameraIds() {
        if (cameraIds.isEmpty() && !camera)
            return false;
        cameraIds.clear();
        camera = false;
        return true;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isLocation() {
        return location;
    }

    public boolean isAudio() {
        return audio;
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isCamera() {
        return camera;
    }

    public boolean isVpn() {
        return vpn;
    }

    public boolean isNetworkActive() {
        return networkActive;
    }

    public boolean isWaitingNotification() {
        return waitingNotification;
    }



    public synchronized Map<String, Object> saveState() {
        HashMap<String, Object> state = new HashMap<>();
        state.put("activities", new ArrayList<>(activities));
        state.put("locationListeners", new ArrayList<>(locationListeners));
        state.put("interfaceIds", new ArrayList<>(interfaceIds));
        state.put("recordingIds", new ArrayList<>(recodingIds));
        state.put("cameraIds", new ArrayList<>(cameraIds));
        state.put("visible", visible);
        state.put("location", location);
        state.put("audio", audio);
        state.put("recording", recording);
        state.put("camera", camera);
        state.put("vpn", vpn);
        state.put("networkActive", networkActive);
        state.put("waitingNotification", waitingNotification);
        return state;
    }

    public synchronized void restoreState(Object savedState) {
        if (!(savedState instanceof Map<?, ?> state))
            return;

        activities.clear();
        locationListeners.clear();
        interfaceIds.clear();
        recodingIds.clear();
        cameraIds.clear();

        for (Object value : getList(state, "activities")) {
            if (value instanceof IBinder)
                activities.add((IBinder) value);
        }
        for (Object value : getList(state, "locationListeners")) {
            if (value instanceof IBinder)
                locationListeners.add((IBinder) value);
        }
        for (Object value : getList(state, "interfaceIds")) {
            if (value instanceof Integer)
                interfaceIds.add((Integer) value);
        }
        for (Object value : getList(state, "recordingIds")) {
            if (value instanceof Integer)
                recodingIds.add((Integer) value);
        }
        for (Object value : getList(state, "cameraIds")) {
            if (value instanceof String)
                cameraIds.add((String) value);
        }

        visible = getBoolean(state, "visible") || !activities.isEmpty();
        location = getBoolean(state, "location") || !locationListeners.isEmpty();
        audio = getBoolean(state, "audio") || !interfaceIds.isEmpty();
        recording = getBoolean(state, "recording") || !recodingIds.isEmpty();
        camera = getBoolean(state, "camera") || !cameraIds.isEmpty();
        vpn = getBoolean(state, "vpn");
        networkActive = getBoolean(state, "networkActive");
        // waitingNotification 不恢复：对应的超时轮询 Runnable 无法随状态重建，
        // 若恢复为 true 该应用将因 WAITING_PUSH_RESPONSE 豁免而永远不被冻结
        waitingNotification = false;
    }

    private static List<?> getList(Map<?, ?> state, String key) {
        Object value = state.get(key);
        return value instanceof List<?> ? (List<?>) value : List.of();
    }

    private static boolean getBoolean(Map<?, ?> state, String key) {
        Object value = state.get(key);
        return value instanceof Boolean && (Boolean) value;
    }
}
