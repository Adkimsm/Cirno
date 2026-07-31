package nep.timeline.cirno.reflect;

import androidx.annotation.NonNull;

import java.lang.reflect.Executable;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class CakeHooker {
    private static XposedModule xposedModule;
    private static ClassLoader hostClassLoader;

    private CakeHooker() {
    }

    public static void setXposedModule(XposedModule module) {
        xposedModule = module;
    }

    public static void clearXposedModule() {
        xposedModule = null;
    }

    public static void setHostClassLoader(ClassLoader classLoader) {
        hostClassLoader = classLoader;
    }

    public static void clearHostClassLoader() {
        hostClassLoader = null;
    }

    public static ClassLoader getHostClassLoader() {
        return hostClassLoader;
    }

    public interface BeforeCallback {
        void call(BeforeHookCallback callback);
    }

    public interface AfterCallback {
        void call(AfterHookCallback callback);
    }

    public interface Callback {
        default void call(BeforeHookCallback callback) {
        }

        default void call(AfterHookCallback callback) {
        }
    }

    public interface ReplacementCallback extends Callback {
        Object call(XposedInterface.Chain chain) throws Throwable;
    }

    public static final class BeforeHookCallback {
        private final XposedInterface.Chain chain;
        private boolean skipped;
        private Object skipResult;
        private boolean thrown;
        private Throwable throwable;
        private Object[] cachedArgs;

        public BeforeHookCallback(XposedInterface.Chain chain) {
            this.chain = chain;
        }

        public Object getThisObject() {
            return chain.getThisObject();
        }

        public Object[] getArgs() {
            Object[] args = cachedArgs;
            if (args == null) {
                args = chain.getArgs().toArray();
                cachedArgs = args;
            }
            return args;
        }

        public Object invokeOriginalMethod() throws Throwable {
            return chain.proceed();
        }

        public Executable getExecutable() {
            return chain.getExecutable();
        }

        public void returnAndSkip(Object result) {
            skipped = true;
            skipResult = result;
        }

        public void throwAndSkip(@NonNull Throwable throwable) {
            thrown = true;
            this.throwable = throwable;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public Object getSkipResult() {
            return skipResult;
        }

        public boolean isThrown() {
            return thrown;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }

    public static final class AfterHookCallback {
        private final XposedInterface.Chain chain;
        public Object result;
        public Throwable throwable;
        private Object[] cachedArgs;

        public AfterHookCallback(XposedInterface.Chain chain, Object result, Throwable throwable) {
            this.chain = chain;
            this.result = result;
            this.throwable = throwable;
        }

        public Object getThisObject() {
            return chain.getThisObject();
        }

        public Object[] getArgs() {
            Object[] args = cachedArgs;
            if (args == null) {
                args = chain.getArgs().toArray();
                cachedArgs = args;
            }
            return args;
        }
    }

    private static final class CustomHooker implements XposedInterface.Hooker {
        private final BeforeCallback beforeCallback;
        private final AfterCallback afterCallback;
        private final Callback callback;
        private final boolean useCallback;

        private CustomHooker(BeforeCallback beforeCallback, AfterCallback afterCallback) {
            this.beforeCallback = beforeCallback != null ? beforeCallback : _ -> {};
            this.afterCallback = afterCallback != null ? afterCallback : _ -> {};
            this.callback = null;
            this.useCallback = false;
        }

        private CustomHooker(Callback callback) {
            this.beforeCallback = null;
            this.afterCallback = null;
            this.callback = callback;
            this.useCallback = true;
        }

        @Override
        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
            if (useCallback && callback instanceof ReplacementCallback replacementCallback) {
                return replacementCallback.call(chain);
            }

            Object result = null;
            Throwable throwable = null;
            boolean skipped = false;
            boolean thrown = false;

            BeforeHookCallback before = new BeforeHookCallback(chain);
            try {
                if (useCallback) {
                    callback.call(before);
                } else {
                    beforeCallback.call(before);
                }
            } catch (Throwable t) {
                // Hook 回调内部异常绝不能穿透进 system_server 的被 hook 方法调用方
                nep.timeline.cirno.log.Log.e("Hook before 回调异常: " + chain.getExecutable(), t);
            }

            if (before.isSkipped()) {
                result = before.getSkipResult();
                skipped = true;
            }
            if (before.isThrown()) {
                throwable = before.getThrowable();
                thrown = true;
            }

            if (!skipped && !thrown) {
                try {
                    result = chain.proceed();
                } catch (Throwable t) {
                    throwable = t;
                }
            }

            AfterHookCallback after = new AfterHookCallback(chain, result, throwable);
            try {
                if (useCallback) {
                    callback.call(after);
                } else {
                    afterCallback.call(after);
                }
            } catch (Throwable t) {
                nep.timeline.cirno.log.Log.e("Hook after 回调异常: " + chain.getExecutable(), t);
            }

            if (after.throwable != null) {
                throw after.throwable;
            }
            return after.result;
        }
    }

    public static XposedInterface.HookHandle hookBefore(Executable executable, BeforeCallback callback) {
        return requireModule().hook(executable).intercept(new CustomHooker(callback, null));
    }

    public static XposedInterface.HookHandle hookAfter(Executable executable, AfterCallback callback) {
        return requireModule().hook(executable).intercept(new CustomHooker(null, callback));
    }

    public static XposedInterface.HookHandle hook(Executable executable, Callback callback) {
        return requireModule().hook(executable).intercept(new CustomHooker(callback));
    }

    public static XposedInterface.HookHandle hook(Executable executable) {
        return requireModule().hook(executable).intercept(chain -> chain.proceed());
    }

    private static XposedModule requireModule() {
        if (xposedModule == null) {
            throw new IllegalStateException("Xposed module is not initialized");
        }
        return xposedModule;
    }
}
