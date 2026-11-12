package nep.timeline.cirno.hooks.android.xiaomi;

import nep.timeline.cirno.framework.MethodHook;

public class XiaomiHooks {
    private static XiaomiHooks instance;
    private final MethodHook binderTransHook;
    private final MethodHook reportNetHook;
    private final MethodHook reportSignalHook;

    public XiaomiHooks(ClassLoader classLoader) {
        instance = this;
        binderTransHook = new nep.timeline.cirno.hooks.android.binder.MilletBinderTransHook(classLoader);
        reportNetHook = new ReportNetHook(classLoader);
        reportSignalHook = new ReportSignalHook(classLoader);
    }

    public void install() {
        binderTransHook.install();
        reportNetHook.install();
        reportSignalHook.install();
    }

    public static boolean isAvailable() {
        return instance != null && instance.binderTransHook.isHooked();
    }

    public void unhookAll() {
        binderTransHook.unhook();
        reportNetHook.unhook();
        reportSignalHook.unhook();
    }
}
