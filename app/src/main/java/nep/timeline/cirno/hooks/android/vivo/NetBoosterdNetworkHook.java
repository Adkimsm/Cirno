package nep.timeline.cirno.hooks.android.vivo;

import java.lang.reflect.Field;
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
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.services.MonitorBinderHub;
import nep.timeline.cirno.utils.SystemChecker;

/**
 * Android 12-14 的 vivo 网络消息事件：由 netboosterd 通过
 * {@code IFreezeNetCtrlUnsolicitedEventListener#onNetPacketInput} 回调到系统侧。
 * 与高版本 {@link FrozenImAppNetworkHook} 策略一致，仅替换入口。
 */
public class NetBoosterdNetworkHook extends MethodHook {
    private static final String FROZEN_IM_APP = "com.android.server.am.frozen.policy.FrozenImApp";
    private static final String TCP_INFO_PARCEL = "android.netboosterd.TcpInfoParcel";
    private static final String NET_EVENT_LISTENER = "android.netboosterd.IFreezeNetCtrlUnsolicitedEventListener";
    private static final String NET_EVENT_METHOD = "onNetPacketInput";
    private static final String NET_EVENT_FIELD = "mNetEventListener";
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    private final Object lock = new Object();
    private volatile boolean enabled = true;
    private volatile boolean skipSupported;
    private volatile XposedInterface.HookHandle listenerHook;

    public NetBoosterdNetworkHook(ClassLoader classLoader) {
        super(classLoader);
        install();
    }

    @Override
    public void startHook() {
        // 父类构造期不安装：此时子类字段尚未初始化，由构造器调用 install()
    }

    @Override
    public String getTargetClass() {
        return FROZEN_IM_APP;
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return null;
    }

    @Override
    public boolean isHooked() {
        return listenerHook != null;
    }

    private void install() {
        try {
            Class<?> imAppClass = CakeReflection.findClassIfExists(FROZEN_IM_APP, classLoader);
            if (imAppClass == null) {
                Log.w("vivo net: 未找到 " + FROZEN_IM_APP + "，跳过 netboosterd 网络事件");
                return;
            }

            if (CakeReflection.findMethodExactIfExists(imAppClass, "onTcpPacketInputCallback", int.class) != null)
                Log.w("vivo net: 检测到 onTcpPacketInputCallback，按版本约定仅使用 netboosterd 路径");

            unhooker = CakeReflection.findAndHookConstructor(imAppClass, new CakeHooker.Callback() {
                @Override
                public void call(CakeHooker.AfterHookCallback callback) {
                    installListenerHook(callback.getThisObject());
                }
            });
            Log.i("FrozenImApp <init> -> 成功Hook完毕!");

            // 热重载或模块晚于 FrozenImApp 构造时，从现有实例补装
            if (MonitorBinderHub.isBootCompleted())
                installFromExistingInstance();
        } catch (Throwable throwable) {
            if (!isIgnoreError())
                Log.e("netboosterd 网络事件 hook 失败", throwable);
        }
    }

    private void installFromExistingInstance() {
        if (!enabled || listenerHook != null)
            return;

        try {
            Class<?> imAppClass = CakeReflection.findClassIfExists(FROZEN_IM_APP, classLoader);
            if (imAppClass == null)
                return;

            Object instance = CakeReflection.callStaticMethod(imAppClass, "getInstance");
            if (instance == null) {
                Log.w("vivo net: FrozenImApp.getInstance() 返回 null，跳过现有实例发现");
                return;
            }

            installListenerHook(instance);
        } catch (Throwable throwable) {
            Log.w("vivo net: 现有实例发现失败", throwable);
        }
    }

    private void installListenerHook(Object frozenImApp) {
        synchronized (lock) {
            if (!enabled || listenerHook != null || frozenImApp == null)
                return;

            try {
                Object listener = findListener(frozenImApp);
                if (listener == null)
                    return;

                Class<?> listenerClass = listener.getClass();
                Class<?> parcelClass = CakeReflection.findClassIfExists(TCP_INFO_PARCEL, classLoader);
                if (parcelClass == null) {
                    Log.w("vivo net: 未找到 " + TCP_INFO_PARCEL + "，跳过网络事件 hook");
                    return;
                }

                Method method = CakeReflection.findMethodExactIfExists(listenerClass, NET_EVENT_METHOD,
                        int.class, int.class, parcelClass);
                if (method == null) {
                    Log.w("vivo net: " + listenerClass.getName() + " 缺少 " + NET_EVENT_METHOD + " 签名，跳过");
                    return;
                }

                skipSupported = method.getReturnType() == void.class;
                if (!skipSupported)
                    Log.w("vivo net: " + NET_EVENT_METHOD + " 返回类型不是 void，将不拦截原生解冻");

                listenerHook = CakeReflection.findAndHookMethod(listenerClass, NET_EVENT_METHOD,
                        int.class, int.class, parcelClass, getEventCallback());
                Log.i(NET_EVENT_METHOD + " -> 成功Hook完毕!");
            } catch (Throwable throwable) {
                if (!isIgnoreError())
                    Log.e(NET_EVENT_METHOD + " hook 失败", throwable);
            }
        }
    }

    private Object findListener(Object frozenImApp) {
        Class<?> imAppClass = frozenImApp.getClass();
        Class<?> listenerType = CakeReflection.findClassIfExists(NET_EVENT_LISTENER, classLoader);
        if (listenerType == null)
            return null;

        Field field = CakeReflection.findFieldIfExists(imAppClass, NET_EVENT_FIELD);
        if (field != null && field.getType() == listenerType)
            return CakeReflection.getObjectField(frozenImApp, NET_EVENT_FIELD);

        // 字段改名时按类型唯一匹配，避免选错同类型的其他字段
        Field only = null;
        int count = 0;
        for (Field candidate : imAppClass.getDeclaredFields()) {
            if (candidate.getType() == listenerType) {
                only = candidate;
                count++;
            }
        }
        if (count != 1) {
            Log.w("vivo net: 找不到唯一的 net 事件 listener 字段");
            return null;
        }

        try {
            only.setAccessible(true);
            return only.get(frozenImApp);
        } catch (Throwable throwable) {
            Log.w("vivo net: 读取 net 事件 listener 失败", throwable);
            return null;
        }
    }

    private CakeHooker.Callback getEventCallback() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                if (!enabled)
                    return;

                try {
                    handleNetworkEvent(callback);
                } catch (Throwable throwable) {
                    Log.w("vivo net: 处理网络事件失败", throwable);
                }
            }
        };
    }

    private void handleNetworkEvent(CakeHooker.BeforeHookCallback callback) {
        if (!FreezeBackend.shouldHandleVivoEvents())
            return;

        Object[] args = callback.getArgs();
        Object tcpInfo = args.length > 2 ? args[2] : null;
        if (tcpInfo == null)
            return;

        int uid = CakeReflection.getIntField(tcpInfo, "uid");

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

    private void skip(CakeHooker.BeforeHookCallback callback) {
        if (skipSupported)
            callback.returnAndSkip(null);
    }

    @Override
    public void unhook() {
        synchronized (lock) {
            enabled = false;

            XposedInterface.HookHandle listener = listenerHook;
            listenerHook = null;
            if (listener != null) {
                try {
                    listener.unhook();
                } catch (Throwable ignored) {
                }
            }

            XposedInterface.HookHandle discovery = unhooker;
            unhooker = null;
            if (discovery != null) {
                try {
                    discovery.unhook();
                } catch (Throwable ignored) {
                }
            }

            hooked = false;
        }
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
