package nep.timeline.cirno.hooks.systemui.tile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;

public class TileClickHook extends MethodHook {
    private static final String TARGET_CLASS = "com.android.systemui.qs.tileimpl.QSTileImpl";
    private static final String TARGET_METHOD = "click";
    private static final String EXPANDABLE_CLASS = "com.android.systemui.animation.Expandable";
    private static final String ACTION_TILE_CLICK = "nep.timeline.cirno.TILE_CLICK";

    public TileClickHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        Object[] params = targetParams();
        if (params == null)
            return new HookTarget[0];
        return new HookTarget[] {
                HookTarget.method(targetClass(), targetMethod(), params, targetCallback())
        };
    }

    private String targetClass() {
        return TARGET_CLASS;
    }

    private String targetMethod() {
        return TARGET_METHOD;
    }

    private Object[] targetParams() {
        Class<?> expandableClass = CakeReflection.findClassIfExists(EXPANDABLE_CLASS, classLoader);
        if (expandableClass == null) {
            Log.e("QSTileImpl -> Expandable 类未找到");
            return null;
        }
        return new Object[]{expandableClass};
    }

    private CakeHooker.Callback targetCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                try {
                    Object tile = callback.getThisObject();
                    Context context = (Context) CakeReflection.getObjectField(tile, "mContext");
                    String tileSpec = (String) CakeReflection.getObjectField(tile, "mTileSpec");
                    Log.d("磁贴被点击: " + tileSpec);

                    String targetPkg = extractPackageFromTileSpec(tileSpec);
                    if (targetPkg == null) {
                        Log.w("非应用磁贴，跳过");
                        return;
                    }

                    if (context == null) {
                        Log.w("无法获取 Context");
                        return;
                    }

                    Log.i("磁贴解冻: " + targetPkg);
                    sendTileClickBroadcast(context, targetPkg);
                } catch (Throwable t) {
                    Log.e("Hook 异常", t);
                }
            }
        };
    }

    private void sendTileClickBroadcast(Context context, String packageName) {
        Intent intent = new Intent(ACTION_TILE_CLICK);
        intent.putExtra("package_name", packageName);
        intent.setPackage("android");
        try {
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            Log.e("广播发送失败", t);
        }
    }

    private static String extractPackageFromTileSpec(String tileSpec) {
        if (tileSpec == null) {
            return null;
        }
        if (tileSpec.startsWith("custom(") && tileSpec.endsWith(")")) {
            String component = tileSpec.substring(7, tileSpec.length() - 1);
            ComponentName cn = ComponentName.unflattenFromString(component);
            if (cn != null) {
                return cn.getPackageName();
            }
        }
        return null;
    }
}
