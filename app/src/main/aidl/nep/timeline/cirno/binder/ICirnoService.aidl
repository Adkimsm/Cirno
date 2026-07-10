package nep.timeline.cirno.binder;

import android.os.Bundle;

interface ICirnoService {
    String getSignal(String key);
    String getStatusSnapshot();
    boolean isPacketAvailable();
    String getHookVersion();

    List<String> getRunningApplication();
    String getProcessesForApp(String packageName, int userId);
    String getNetworkSpeed(String packageName, int userId);

    String isFrozen(String packageName, int userId);
    List<String> getFrozenStates(in List<String> apps);

    Bundle getMonitorSnapshot();
}
