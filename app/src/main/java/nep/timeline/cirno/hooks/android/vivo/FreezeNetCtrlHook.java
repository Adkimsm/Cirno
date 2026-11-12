package nep.timeline.cirno.hooks.android.vivo;

import java.lang.reflect.Constructor;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.VivoFreezeNetCtrlWrapper;
import nep.timeline.cirno.utils.SystemChecker;

public class FreezeNetCtrlHook extends MethodHook {
    private static final String FROZEN_IM_APP = "com.android.server.am.frozen.policy.FrozenImApp";
    private static final String VPSNH_CTRL = "com.android.server.am.frozen.policy.FreezeNetCtrl2";
    private static final String VPSNH_SERVICE = "com.vivo.vpsnh.IVpsnhService";
    private static final String NETBOOSTERD_CTRL = "com.android.server.am.frozen.policy.FreezeNetCtrl1";
    private static final String NETBOOSTERD = "android.netboosterd.INetBoosterd";

    // 不能带初始化器：startHook 在父类构造期间执行，字段初始化会覆盖已安装的 handle
    private XposedInterface.HookHandle netBoosterdUnhooker;

    public FreezeNetCtrlHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return VPSNH_CTRL;
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return getCaptureCallback("FreezeNetCtrl2");
    }

    @Override
    public void startHook() {
        unhooker = hookConstructor(VPSNH_CTRL, VPSNH_SERVICE, "FreezeNetCtrl2");
        netBoosterdUnhooker = hookConstructor(NETBOOSTERD_CTRL, NETBOOSTERD, "FreezeNetCtrl1");

        if (unhooker == null && netBoosterdUnhooker == null) {
            Log.i("vivo net: 未找到 FreezeNetCtrl 构造器，跳过 controller 捕获");
            return;
        }

        hooked = true;
    }

    private XposedInterface.HookHandle hookConstructor(String className, String serviceType, String label) {
        try {
            Class<?> clazz = CakeReflection.findClassIfExists(className, classLoader);
            if (clazz == null)
                return null;

            Constructor<?> constructor = CakeReflection.findConstructorExactIfExists(clazz, FROZEN_IM_APP, serviceType);
            if (constructor == null)
                return null;

            XposedInterface.HookHandle handle = CakeHooker.hook(constructor, getCaptureCallback(label));
            Log.i(label + " <init> -> 成功Hook完毕!");
            return handle;
        } catch (Throwable throwable) {
            if (!isIgnoreError())
                Log.e(label + " hook 失败", throwable);
            return null;
        }
    }

    private CakeHooker.Callback getCaptureCallback(String label) {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                if (callback.throwable != null)
                    return;

                Object instance = callback.getThisObject();
                if (instance == null)
                    return;

                VivoFreezeNetCtrlWrapper.setInstance(instance);
                Log.i(label + " 实例已捕获");
            }
        };
    }

    @Override
    public void unhook() {
        XposedInterface.HookHandle netBoosterd = netBoosterdUnhooker;
        netBoosterdUnhooker = null;
        if (netBoosterd != null) {
            try {
                netBoosterd.unhook();
            } catch (Throwable ignored) {
            }
        }

        XposedInterface.HookHandle vpsnh = unhooker;
        unhooker = null;
        if (vpsnh != null) {
            try {
                vpsnh.unhook();
            } catch (Throwable ignored) {
            }
        }

        hooked = false;
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
