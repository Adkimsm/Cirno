package nep.timeline.cirno.provide;

public interface BatteryOptimizationBinderFacade {
    int BATTERY_OPTIMIZATION_UNKNOWN = -1;
    int BATTERY_OPTIMIZATION_DISABLED = 0;
    int BATTERY_OPTIMIZATION_ENABLED = 1;

    int getBatteryOptimizationState(String packageName, int userId);
    int[] getBatteryOptimizationStates(java.util.List<String> packageNames, int[] userIds);
    boolean isBatteryOptimizationEnabled(String packageName, int userId);
    boolean setBatteryOptimizationEnabled(String packageName, int userId, boolean enabled);
    boolean syncBatteryOptimizationWhitelist();
    void clearBatteryOptimizationPendingSync();
}
