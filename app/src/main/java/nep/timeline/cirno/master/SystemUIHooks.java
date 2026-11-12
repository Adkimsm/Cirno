package nep.timeline.cirno.master;

import nep.timeline.cirno.hooks.systemui.tile.TileClickHook;

public class SystemUIHooks {
    public static void start(ClassLoader classLoader) {
        new TileClickHook(classLoader).install();
    }
}
