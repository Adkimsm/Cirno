package nep.timeline.cirno.hooks.android.broadcast;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.utils.ReflectUtils;

public class AutostartBlockHook extends MethodHook {
    private static final Set<String> LOGGED_BLOCKS = ConcurrentHashMap.newKeySet();

    public AutostartBlockHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "com.android.server.pm.PackageManagerService";
        } else {
            return "com.android.server.pm.ResolveIntentHelper";
        }
    }

    @Override
    public String getTargetMethod() {
        return "queryIntentReceiversInternal";
    }

    @Override
    public Object[] getTargetParam() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+: ResolveIntentHelper with 8 params
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(), "com.android.server.pm.Computer", Intent.class, String.class, long.class, int.class, int.class, int.class, boolean.class);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13-14: ResolveIntentHelper with 7 params
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(), "com.android.server.pm.Computer", Intent.class, String.class, long.class, int.class, int.class, boolean.class);
        } else {
            // Android 12: PackageManagerService with 5 params
            return ReflectUtils.findParameterTypesOrDefault(
                    CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                    getTargetMethod(), Intent.class, String.class, int.class, int.class, boolean.class);
        }
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    if (callback.result == null) return;

                    Object[] args = callback.getArgs();
                    int userIdIndex = getUserIdIndex();
                    int userId = (int) args[userIdIndex];

                    List<?> list = (List<?>) callback.result;
                    if (list == null || list.isEmpty()) return;

                    List<ResolveInfo> filtered = null;
                    for (int i = 0; i < list.size(); i++) {
                        ResolveInfo info = (ResolveInfo) list.get(i);
                        String pkg = (info.activityInfo != null) ? info.activityInfo.packageName : null;
                        if (pkg != null && AppConfigs.isAutostartBlocked(pkg, userId)) {
                            if (LOGGED_BLOCKS.add(pkg + ":" + userId)) {
                                Log.i("AutostartBlockHook: blocked pkg=" + pkg + " userId=" + userId);
                            }
                            if (filtered == null) {
                                filtered = new ArrayList<>(list.size());
                                for (int j = 0; j < i; j++) {
                                    filtered.add((ResolveInfo) list.get(j));
                                }
                            }
                        } else if (filtered != null) {
                            filtered.add(info);
                        }
                    }

                    if (filtered != null) {
                        callback.result = filtered;
                    }
                } catch (Throwable e) {
                    Log.e("AutostartBlockHook: error", e);
                }
            }
        };
    }

    private int getUserIdIndex() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: ResolveIntentHelper has Computer as first param, userId at index 4
            return 4;
        } else {
            // Android 12: PackageManagerService, userId at index 3
            return 3;
        }
    }
}
