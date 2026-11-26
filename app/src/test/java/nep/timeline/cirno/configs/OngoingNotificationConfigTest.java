package nep.timeline.cirno.configs;

import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.configs.settings.ApplicationSettings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OngoingNotificationConfigTest {
    private static final String PACKAGE_NAME = "app.ongoing";
    private final Gson gson = new Gson();
    private ApplicationSettings previous;

    @Before
    public void setUp() {
        previous = GlobalVars.applicationSettings;
        GlobalVars.applicationSettings = new ApplicationSettings();
    }

    @After
    public void tearDown() {
        GlobalVars.applicationSettings = previous;
    }

    @Test
    public void missingOrNullSettingDefaultsToDisabled() {
        restore("{}");
        assertFalse(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 0));
        restore("{\"ongoingNotificationApps\":null}");
        assertFalse(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 0));
    }

    @Test
    public void savedSettingIsScopedToTheConfiguredUser() {
        AppConfigs.setOngoingNotificationAllowed(PACKAGE_NAME, 10, true);
        restore(gson.toJson(GlobalVars.applicationSettings));

        assertTrue(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 10));
        assertFalse(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 0));
    }

    @Test
    public void whitelistClearsAndBlocksOngoingNotificationExemptionForThatUser() {
        AppConfigs.setOngoingNotificationAllowed(PACKAGE_NAME, 0, true);
        AppConfigs.setOngoingNotificationAllowed(PACKAGE_NAME, 10, true);

        AppConfigs.setWhiteApp(PACKAGE_NAME, 10, true);
        assertFalse(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 10));
        AppConfigs.setOngoingNotificationAllowed(PACKAGE_NAME, 10, true);
        assertFalse(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 10));
        assertTrue(AppConfigs.isOngoingNotificationAllowed(PACKAGE_NAME, 0));
    }

    private void restore(String json) {
        GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(
                gson.fromJson(json, ApplicationSettings.class));
    }
}
