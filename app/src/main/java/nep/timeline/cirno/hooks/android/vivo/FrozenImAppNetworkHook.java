package nep.timeline.cirno.hooks.android.vivo;

import java.lang.reflect.Method;
import java.util.List;

import nep.timeline.cirno.configs.checkers.AppConfigs;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.OverloadMethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.ActivityManagerService;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.SystemChecker;

public class FrozenImAppNetworkHook extends OverloadMethodHook {
    private static final String TARGET_CLASS = "com.android.server.am.frozen.policy.FrozenImApp";
    private static final String TARGET_METHOD = "onTcpPacketInputCallback";
    private static final String TCP_INFO = "com.android.server.am.frozen.policy.TcpInfo";
    private static final String WECHAT = "com.tencent.mm";
    private static final String QOS_INJECTOR = "com.vivo.services.qos.QoSInjectorImpl";
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

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
    protected Object[][] getPreferredParamTypes() {
        Class<?> tcpInfo = CakeReflection.findClassIfExists(TCP_INFO, classLoader);
        if (tcpInfo == null)
            return new Object[][]{{int.class}};
        return new Object[][]{{int.class}, {tcpInfo}};
    }

    @Override
    protected boolean shouldHookFallback(Method method) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length != 1 || method.getReturnType() != void.class)
            return false;
        return types[0] == int.class || TCP_INFO.equals(types[0].getName());
    }

    @Override
    protected HookMode getHookMode() {
        return HookMode.BEFORE;
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                Class<?>[] types = callback.getExecutable().getParameterTypes();
                if (types.length != 1)
                    return;

                if (types[0] == int.class)
                    handleUid(callback);
                else if (TCP_INFO.equals(types[0].getName()))
                    handleTcpInfo(callback);
            }
        };
    }

    /**
     * 旧入口：onTcpPacketInputCallback(int uid)
     */
    private void handleUid(CakeHooker.BeforeHookCallback callback) {
        if (!FreezeBackend.shouldHandleVivoEvents())
            return;

        int uid = (int) callback.getArgs()[0];

        List<AppRecord> appRecords = AppService.getByUid(uid);
        if (appRecords.isEmpty()) {
            skip(callback);
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
            skip(callback);
    }

    /**
     * 新入口：onTcpPacketInputCallback(TcpInfo)
     * skip 后会连带跳过微信 VOIP 的 QoS 通知，需要由这边补一次。
     */
    private void handleTcpInfo(CakeHooker.BeforeHookCallback callback) {
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
        for (AppRecord appRecord : AppService.getByUid(uid)) {
            if (appRecord == null)
                continue;
            if (!AppConfigs.isNetworkMessageAllowed(appRecord.getPackageName(), appRecord.getUserId()))
                continue;
            allowed = true;
            FreezerService.temporaryUnfreezeIfNeed(appRecord, "Vivo Network", TEMP_UNFREEZE_INTERVAL_MS);
        }

        if (allowed)
            return;

        if (skip(callback))
            notifyWechatQos(uid, tcpInfo);
    }

    private static boolean skip(CakeHooker.BeforeHookCallback callback) {
        if (!(callback.getExecutable() instanceof Method method) || method.getReturnType() != void.class)
            return false;

        callback.returnAndSkip(null);
        return true;
    }

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
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
