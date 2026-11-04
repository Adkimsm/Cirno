package nep.timeline.cirno.utils;

import java.lang.reflect.Method;
import java.util.Objects;

import nep.timeline.cirno.log.Log;

public class ReflectUtils {
    public static Object[] findParameterTypesOrDefault(Class<?> clazz, String methodName, Object... parameter) {
        if (clazz == null) return parameter;
        try {
            StringBuilder candidates = new StringBuilder();
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    candidates.append("  ").append(java.util.Arrays.toString(parameterTypes)).append("\n");

                    if (parameter.length <= parameterTypes.length) {
                        boolean isCompatible = true;

                        for (int i = 0; i < parameter.length; i++) {
                            Object obj = parameter[i];

                            if (!Objects.equals(obj, obj instanceof String ? parameterTypes[i].getName() : parameterTypes[i])) {
                                isCompatible = false;
                                break;
                            }
                        }

                        if (isCompatible) {
                            return parameterTypes;
                        }
                    }
                }
            }
            if (candidates.length() > 0) {
                Log.d("[ReflectUtils-DEBUG] " + methodName + " 无匹配, 候选:\n" + candidates);
            } else {
                Log.d("[ReflectUtils-DEBUG] " + methodName + " 未找到任何同名方法");
            }
        } catch (Exception e) {
            Log.e("Find parameter", e);
        }

        return parameter;
    }
}
