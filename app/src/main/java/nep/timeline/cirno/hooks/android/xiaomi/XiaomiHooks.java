package nep.timeline.cirno.hooks.android.xiaomi;

import nep.timeline.cirno.framework.MethodHook;

public class XiaomiHooks {
    private final MethodHook binderTransHook;
    private final MethodHook reportNetHook;
    private final MethodHook reportSignalHook;

    public XiaomiHooks(ClassLoader classLoader) {
        binderTransHook = new nep.timeline.cirno.hooks.android.binder.MilletBinderTransHook(classLoader);
        reportNetHook = new ReportNetHook(classLoader);
        reportSignalHook = new ReportSignalHook(classLoader);
    }

    public boolean isAvailable() {
        return binderTransHook.isHooked();
    }

    public void unhookAll() {
        if (binderTransHook.isHooked()) binderTransHook.unhook();
        if (reportNetHook.isHooked()) reportNetHook.unhook();
        if (reportSignalHook.isHooked()) reportSignalHook.unhook();
    }
}
