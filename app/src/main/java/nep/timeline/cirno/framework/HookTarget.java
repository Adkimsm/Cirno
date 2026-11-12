package nep.timeline.cirno.framework;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;

public final class HookTarget {
    public interface Resolver {
        List<? extends Executable> resolve(ClassLoader classLoader) throws Throwable;
    }

    public interface CallbackFactory {
        CakeHooker.Callback callbackFor(Executable executable);
    }

    private final String label;
    private final Resolver resolver;
    private final CallbackFactory callbackFactory;

    private HookTarget(String label, Resolver resolver, CallbackFactory callbackFactory) {
        this.label = label;
        this.resolver = resolver;
        this.callbackFactory = callbackFactory;
    }

    public static HookTarget method(String className, String methodName,
                                    Object[] parameterTypes, CakeHooker.Callback callback) {
        return new HookTarget(className + "#" + methodName,
                classLoader -> {
                    Class<?> clazz = CakeReflection.findClassIfExists(className, classLoader);
                    if (clazz == null)
                        return List.of();
                    Method method = CakeReflection.findMethodExactIfExists(clazz, methodName, parameterTypes);
                    return method == null ? List.of() : List.of(method);
                },
                executable -> callback);
    }

    public static HookTarget constructor(String className, Object[] parameterTypes,
                                         CakeHooker.Callback callback) {
        return new HookTarget(className + ".<init>",
                classLoader -> {
                    Class<?> clazz = CakeReflection.findClassIfExists(className, classLoader);
                    if (clazz == null)
                        return List.of();
                    Constructor<?> constructor = CakeReflection.findConstructorExactIfExists(clazz, parameterTypes);
                    return constructor == null ? List.of() : List.of(constructor);
                },
                executable -> callback);
    }

    public static HookTarget overloads(String className, String methodName,
                                       Object[][] preferredParameterTypes,
                                       Predicate<Method> fallbackFilter,
                                       CakeHooker.Callback callback) {
        return new HookTarget(className + "#" + methodName,
                classLoader -> {
                    Class<?> clazz = CakeReflection.findClassIfExists(className, classLoader);
                    if (clazz == null)
                        return List.of();

                    Set<Executable> result = new LinkedHashSet<>();
                    if (preferredParameterTypes != null) {
                        for (Object[] parameterTypes : preferredParameterTypes) {
                            Method method = CakeReflection.findMethodExactIfExists(
                                    clazz, methodName, parameterTypes);
                            if (method != null)
                                result.add(method);
                        }
                    }

                    if (result.isEmpty() && fallbackFilter != null) {
                        for (Method method : clazz.getDeclaredMethods()) {
                            if (fallbackFilter.test(method))
                                result.add(method);
                        }
                    }
                    return new ArrayList<>(result);
                },
                executable -> callback);
    }

    public static HookTarget resolve(String label, Resolver resolver, CallbackFactory callbackFactory) {
        return new HookTarget(label, resolver, callbackFactory);
    }

    String label() {
        return label;
    }

    List<Executable> resolve(ClassLoader classLoader) throws Throwable {
        List<? extends Executable> resolved = resolver.resolve(classLoader);
        return resolved == null ? List.of() : new ArrayList<>(resolved);
    }

    CakeHooker.Callback callbackFor(Executable executable) {
        return callbackFactory.callbackFor(executable);
    }
}
