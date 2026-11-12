package nep.timeline.cirno.hooks.android.vivo;

import android.os.IBinder;

import java.lang.reflect.Field;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.services.VivoFreezeNetCtrlWrapper;
import nep.timeline.cirno.utils.SystemChecker;

/**
 * Android 12-14 的 vivo 网络追踪控制器来自 netboosterd 的 AIDL 接口，
 * 取代高版本 {@code FreezeNetCtrl2} 的进程内实现。
 */
public class NetBoosterdCtrlHook extends MethodHook {
    private static final String CTRL_STUB = "android.netboosterd.IFreezeNetCtrl$Stub";
    private static final String CTRL_INTERFACE = "android.netboosterd.IFreezeNetCtrl";
    private static final String FREEZE_NET_CTRL2 = "com.android.server.am.frozen.policy.FreezeNetCtrl2";
    private static final String FROZEN_IM_APP = "com.android.server.am.frozen.policy.FrozenImApp";
    private static final String CTRL_FIELD = "mFreezeNetCtrl";
    private static final String TARGET_METHOD = "asInterface";

    private volatile Class<?> ctrlInterface;

    public NetBoosterdCtrlHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        Class<?> stubClass = CakeReflection.findClassIfExists(CTRL_STUB, classLoader);
        ctrlInterface = CakeReflection.findClassIfExists(CTRL_INTERFACE, classLoader);
        if (stubClass == null || ctrlInterface == null) {
            Log.w("vivo net: 未找到 " + CTRL_STUB + "，跳过 controller hook");
            return new HookTarget[0];
        }

        if (CakeReflection.findClassIfExists(FREEZE_NET_CTRL2, classLoader) != null)
            Log.w("vivo net: 检测到 FreezeNetCtrl2，按版本约定仅使用 IFreezeNetCtrl");

        if (CakeReflection.findMethodExactIfExists(stubClass, TARGET_METHOD, IBinder.class) == null) {
            Log.w("vivo net: " + CTRL_STUB + " 缺少 asInterface(IBinder)，跳过 controller hook");
            return new HookTarget[0];
        }

        return new HookTarget[] {
                HookTarget.method(CTRL_STUB, TARGET_METHOD, new Object[]{IBinder.class},
                        new CakeHooker.Callback() {
                            @Override
                            public void call(CakeHooker.AfterHookCallback callback) {
                                capture(callback.result);
                            }
                        })
        };
    }

    @Override
    protected void onHooked() {
        // 热重载或模块晚于 netboosterd 初始化时，从现有实例补获
        if (MonitorBinderHub.isBootCompleted())
            captureFromExistingInstance();
    }

    private void capture(Object ctrl) {
        if (!isActive() || ctrl == null)
            return;

        Class<?> type = ctrlInterface;
        if (type != null && !type.isInstance(ctrl))
            return;

        VivoFreezeNetCtrlWrapper.setInstance(ctrl);
        Log.i("IFreezeNetCtrl 实例已捕获");
    }

    private void captureFromExistingInstance() {
        if (!isActive() || VivoFreezeNetCtrlWrapper.getInstance() != null)
            return;

        try {
            Class<?> imAppClass = CakeReflection.findClassIfExists(FROZEN_IM_APP, classLoader);
            if (imAppClass == null)
                return;

            Object instance = CakeReflection.callStaticMethod(imAppClass, "getInstance");
            if (instance == null)
                return;

            capture(readCtrlField(imAppClass, instance));
        } catch (Throwable throwable) {
            Log.w("vivo net: 现有 controller 发现失败", throwable);
        }
    }

    private Object readCtrlField(Class<?> imAppClass, Object instance) throws IllegalAccessException {
        Class<?> type = ctrlInterface;
        if (type == null)
            return null;

        Field field = CakeReflection.findFieldIfExists(imAppClass, CTRL_FIELD);
        if (field != null && field.getType() == type)
            return field.get(instance);

        // 字段改名时按类型唯一匹配
        Field only = null;
        int count = 0;
        for (Field candidate : imAppClass.getDeclaredFields()) {
            if (candidate.getType() == type) {
                only = candidate;
                count++;
            }
        }
        if (count != 1)
            return null;

        only.setAccessible(true);
        return only.get(instance);
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
