package nep.timeline.cirno.provide;

import java.util.List;

public interface ApplicationBinderFacade {
    List<String> getRunningApplication();
    String getProcessesForApp(String packageName, int userId);
    String getRunningProcessesForApp(String packageName, int userId);
    String getNetworkSpeed(String packageName, int userId);
}
