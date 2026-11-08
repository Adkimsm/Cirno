package nep.timeline.cirno.services;

import lombok.Getter;
import lombok.Setter;

public class CachedAppOptimizer {
    @Getter
    @Setter
    public static volatile Object instance;

    public static void restoreInstance(Object obj) {
        instance = obj;
    }
}
