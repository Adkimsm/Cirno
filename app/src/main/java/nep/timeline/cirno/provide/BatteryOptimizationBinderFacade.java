package nep.timeline.cirno.provide;

public interface BatteryOptimizationBinderFacade {
    boolean isBatteryOptimizationEnabled(String packageName, int userId);
    boolean setBatteryOptimizationEnabled(String packageName, int userId, boolean enabled);
    boolean syncBatteryOptimizationWhitelist();
}
