package nep.timeline.cirno.hooks.android.vivo;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class FrozenImAppNetworkHook extends MethodHook {
    private static final String TARGET_CLASS = "com.android.server.am.frozen.policy.FrozenImApp";
    private static final String TARGET_METHOD = "onTcpPacketInputCallback";
    private static final String TCP_INFO = "com.android.server.am.frozen.policy.TcpInfo";
    private static final String WECHAT = "com.tencent.mm";
    private static final String QOS_INJECTOR = "com.vivo.services.qos.QoSInjectorImpl";
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    // 不能带初始化器：startHook 在父类构造期间执行，字段初始化会覆盖已安装的 handle
    private XposedInterface.HookHandle tcpInfoUnhooker;

    public FrozenImAppNetworkHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return TARGET_CLASS;
    }

    @Override
    public String getTargetMethod() {
        return TARGET_METHOD;
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{int.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return getUidCallback();
    }

    @Override
    public void startHook() {
        try {
            Class<?> targetClass = CakeReflection.findClassIfExists(TARGET_CLASS, classLoader);
            if (targetClass == null) {
                Log.i("vivo net: 未找到 " + TARGET_CLASS + "，跳过网络事件");
                return;
            }

            boolean found = false;
            boolean installed = false;

            Method uidMethod = CakeReflection.findMethodExactIfExists(targetClass, TARGET_METHOD, int.class);
            if (uidMethod != null) {
                found = true;
                unhooker = CakeHooker.hook(uidMethod, getUidCallback());
                installed = true;
                Log.i(TARGET_METHOD + "(int) -> 成功Hook完毕!");
            }

            Class<?> tcpInfoClass = CakeReflection.findClassIfExists(TCP_INFO, classLoader);
            Method tcpInfoMethod = tcpInfoClass == null ? null
                    : CakeReflection.findMethodExactIfExists(targetClass, TARGET_METHOD, tcpInfoClass);
            if (tcpInfoMethod != null) {
                found = true;
                if (tcpInfoMethod.getReturnType() != void.class) {
                    if (!isIgnoreError())
                        Log.e(TARGET_METHOD + "(TcpInfo) 返回类型不是 void，跳过");
                } else {
                    tcpInfoUnhooker = CakeHooker.hook(tcpInfoMethod, getTcpInfoCallback());
                    installed = true;
                    Log.i(TARGET_METHOD + "(TcpInfo) -> 成功Hook完毕!");
                }
            }

            if (!installed) {
                if (!found)
                    Log.i("vivo net: " + TARGET_CLASS + " 未找到网络事件入口，跳过");
                return;
            }

            hooked = true;
        } catch (Throwable throwable) {
            if (!isIgnoreError())
                Log.e(TARGET_METHOD + " hook 失败", throwable);
        }
    }

    private CakeHooker.Callback getUidCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleVivoEvents())
                    return;

                int uid = (int) callback.getArgs()[0];

                List<AppRecord> appRecords = AppService.getByUid(uid);
                if (appRecords.isEmpty()) {
                    callback.returnAndSkip(null);
                    return;
                }

                boolean allowed = false;
                for (AppRecord appRecord : appRecords) {
                    if (appRecord == null)
                        continue;
                    if (!AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId()))
                        continue;
                    allowed = true;
                    FreezerService.temporaryUnfreezeIfNeed(appRecord, "Vivo Network", TEMP_UNFREEZE_INTERVAL_MS);
                }

                if (!allowed)
                    callback.returnAndSkip(null);
            }
        };
    }

    private CakeHooker.Callback getTcpInfoCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!FreezeBackend.shouldHandleVivoEvents())
                    return;

                Object tcpInfo = callback.getArgs()[0];
                if (tcpInfo == null)
                    return;

                int uid;
                try {
                    uid = readInt(tcpInfo, "getUid", "uid");
                } catch (Throwable throwable) {
                    Log.d("vivo net: 读取 TcpInfo.uid 失败 " + throwable);
                    return;
                }

                boolean allowed = false;
                List<AppRecord> appRecords = AppService.getByUid(uid);
                for (AppRecord appRecord : appRecords) {
                    if (appRecord == null)
                        continue;
                    if (!AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId()))
                        continue;
                    allowed = true;
                    FreezerService.temporaryUnfreezeIfNeed(appRecord, "Vivo Network", TEMP_UNFREEZE_INTERVAL_MS);
                }

                if (allowed)
                    return;

                callback.returnAndSkip(null);
                notifyWechatQos(uid, tcpInfo);
            }
        };
    }

    /**
     * 高版本把微信 VOIP 的 QoS 通知放在同一个回调里，skip 后需要由这边补一次。
     */
    private void notifyWechatQos(int uid, Object tcpInfo) {
        try {
            if (!isWechatUid(uid))
                return;

            int pid = readInt(tcpInfo, "getPid", "pid");

            Class<?> qosInjector = CakeReflection.findClassIfExists(QOS_INJECTOR, classLoader);
            if (qosInjector == null)
                return;

            Object injector = CakeReflection.callStaticMethod(qosInjector, "getInstance");
            if (injector == null)
                return;

            CakeReflection.callMethod(injector, "notifyVoipPhoneCallScene", uid, pid, WECHAT);
        } catch (Throwable throwable) {
            Log.d("vivo net: 补调 notifyVoipPhoneCallScene 失败 " + throwable);
        }
    }

    private boolean isWechatUid(int uid) {
        try {
            String[] packages = ActivityManagerService.getPackagesForUid(uid);
            if (packages == null)
                return false;

            for (String pkg : packages) {
                if (pkg == null)
                    continue;
                int index = pkg.indexOf(':');
                String name = index > 0 ? pkg.substring(0, index) : pkg;
                if (WECHAT.equals(name))
                    return true;
            }
        } catch (Throwable throwable) {
            Log.d("vivo net: 查询 uid 包名失败 " + throwable);
        }
        return false;
    }

    private static int readInt(Object target, String getter, String field) {
        try {
            if (CakeReflection.callMethod(target, getter) instanceof Integer value)
                return value;
        } catch (Throwable ignored) {
        }
        return CakeReflection.getIntField(target, field);
    }

    @Override
    public void unhook() {
        XposedInterface.HookHandle tcpInfo = tcpInfoUnhooker;
        tcpInfoUnhooker = null;
        if (tcpInfo != null) {
            try {
                tcpInfo.unhook();
            } catch (Throwable ignored) {
            }
        }

        XposedInterface.HookHandle uid = unhooker;
        unhooker = null;
        if (uid != null) {
            try {
                uid.unhook();
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
