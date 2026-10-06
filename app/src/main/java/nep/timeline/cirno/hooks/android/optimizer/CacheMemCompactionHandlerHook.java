package nep.timeline.cirno.hooks.android.optimizer;

import android.os.Build;
import android.os.Message;

import java.util.List;

import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;

/**
 * Drops invalid process entries before CachedAppOptimizer dereferences them.
 *
 * <p>Some vendor CachedAppOptimizer implementations can leave a null entry in
 * mPendingCompactionProcesses while a process is being removed. AOSP's handler
 * assumes that every entry is valid and reads mOptRecord without a null check.
 */
public class CacheMemCompactionHandlerHook extends MethodHook {
    private static final int COMPACT_PROCESS_MSG = 1;

    public CacheMemCompactionHandlerHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.CachedAppOptimizer$MemCompactionHandler";
    }

    @Override
    public String getTargetMethod() {
        return "handleMessage";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{"android.os.Message"};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Object[] args = callback.getArgs();
                if (args.length == 0 || !(args[0] instanceof Message))
                    return;

                Message message = (Message) args[0];
                if (message.what != COMPACT_PROCESS_MSG)
                    return;

                if (dropInvalidPendingProcess(callback.getThisObject()))
                    callback.returnAndSkip(null);
            }
        };
    }

    private static boolean dropInvalidPendingProcess(Object handler) {
        try {
            Object optimizer = CakeReflection.getSurroundingThis(handler);
            Object pendingLock = CakeReflection.getObjectField(optimizer, "mProcLock");
            Object pendingObject = CakeReflection.getObjectField(
                    optimizer, "mPendingCompactionProcesses");
            if (!(pendingObject instanceof List<?>))
                return false;

            List<?> pending = (List<?>) pendingObject;
            if (pendingLock == null)
                return removeInvalidHead(pending);

            synchronized (pendingLock) {
                return removeInvalidHead(pending);
            }
        } catch (Throwable ignored) {
            // Keep the platform implementation unchanged on an incompatible ROM.
            return false;
        }
    }

    private static boolean removeInvalidHead(List<?> pending) {
        if (pending.isEmpty())
            return false;

        Object process = pending.get(0);
        if (process == null || isMissingOptRecord(process)) {
            pending.remove(0);
            return true;
        }
        return false;
    }

    private static boolean isMissingOptRecord(Object process) {
        try {
            return CakeReflection.getObjectField(process, "mOptRecord") == null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public int getMinVersion() {
        return Build.VERSION_CODES.S;
    }

}
