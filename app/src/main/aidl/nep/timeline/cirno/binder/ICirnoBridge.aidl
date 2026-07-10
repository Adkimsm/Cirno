package nep.timeline.cirno.binder;

import nep.timeline.cirno.binder.ICirnoService;

interface ICirnoBridge {
    void registerHookBinder(ICirnoService service);
    ICirnoService getHookBinder();
    boolean isHookBinderAlive();
}
