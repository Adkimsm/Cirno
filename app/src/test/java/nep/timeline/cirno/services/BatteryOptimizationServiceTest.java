package nep.timeline.cirno.services;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class BatteryOptimizationServiceTest {
    @After
    public void resetController() throws Exception {
        setController(null);
    }

    @Test
    public void batchReadsOneSnapshotAndPreservesOrderAcrossUsers() throws Exception {
        Controller controller = new Controller();
        controller.mPowerSaveWhitelistUserApps.put("app.allowlisted", 10001);
        setController(controller);

        int[] states = BatteryOptimizationService.getBatteryOptimizationStates(
                List.of("app.optimized", "app.allowlisted", "app.allowlisted", "", "app.invalidUser"),
                new int[]{0, 0, 10, 0, -1});

        assertArrayEquals(new int[]{1, 0, 0, -1, -1}, states);
        assertEquals(1, controller.mPowerSaveWhitelistUserApps.reads);
    }

    @Test
    public void missingControllerKeepsEveryStateUnknown() {
        assertArrayEquals(new int[]{-1, -1}, BatteryOptimizationService.getBatteryOptimizationStates(
                List.of("app.one", "app.two"), new int[]{0, 10}));
    }

    @Test(expected = IllegalArgumentException.class)
    public void mismatchedUsersAreRejected() {
        BatteryOptimizationService.getBatteryOptimizationStates(List.of("app.one"), new int[0]);
    }

    private static void setController(Object value) throws Exception {
        // Avoid the real setter's system_server scheduling side effects in this unit test.
        Field field = BatteryOptimizationService.class.getDeclaredField("controller");
        field.setAccessible(true);
        field.set(null, value);
    }

    public static final class Controller {
        public final CountingWhitelist mPowerSaveWhitelistUserApps = new CountingWhitelist();
    }

    public static final class CountingWhitelist extends HashMap<String, Integer> {
        int reads;

        @Override
        public Set<String> keySet() {
            reads++;
            return super.keySet();
        }
    }
}
