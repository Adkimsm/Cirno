package nep.timeline.cirno.hooks.android.vivo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.hooks.android.freeze.FreezeBackend;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.utils.SystemChecker;

/**
 * 从源头拒绝 vivo 的冻结请求。
 *
 * <p>进程被冻结后无法处理 binder 事务，冻结成功时 vivo 还会主动关闭 socket、回收
 * WiFi/GPS/Sensor/Wakelock/Alarm/Job 等资源，事后靠 BBL/BFL 或网络事件解冻必然存在延迟。
 * 直接拒绝冻结可以让进程始终可调度，binder 与网络保持原样。
 *
 * <p>覆盖两代 vivo-services：
 * <ul>
 *   <li>{@code FreezeRecord.doFreeze(FreezeRequest, boolean)}：两代共用的应用级冻结执行点，
 *       正常请求与强制请求（forceFreeze）都会走到这里；
 *   <li>{@code FreezerManager.freezeProcess(ProcessInfo)}：新版本新增的扩展进程直通冻结路径，
 *       不经过 FreezeRecord；
 *   <li>{@code AppQuickFreezer.requestFreeze(...)}：旧版本 RMS 快速冻结入口，会先记账并发送
 *       广播代理再调用真正的冻结，需要提前拦截。
 * </ul>
 */
public class VivoFreezeDenyHook extends MethodHook {
    private static final String FREEZE_RECORD = "com.vivo.services.freezer.FreezeRecord";
    private static final String FREEZER_MANAGER = "com.vivo.services.freezer.FreezerManager";
    private static final String APP_QUICK_FREEZER = "com.vivo.services.rms.freezer.AppQuickFreezer";
    private static final String FREEZE_REQUEST = "com.vivo.services.freezer.FreezeRequest";
    private static final String PROCESS_INFO = "com.vivo.services.rms.appmng.ProcessInfo";

    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();

    public VivoFreezeDenyHook(ClassLoader classLoader) {
        super(classLoader);
        install();
    }

    @Override
    public void startHook() {
        // 父类构造期不安装：此时子类字段尚未初始化，由构造器调用 install()
    }

    @Override
    public String getTargetClass() {
        return FREEZE_RECORD;
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return null;
    }

    @Override
    public boolean isHooked() {
        return !handles.isEmpty();
    }

    private void install() {
        installFreezeRecord();
        installFreezeProcess();
        installQuickFreezer();
    }

    private void installFreezeRecord() {
        installMethod(FREEZE_RECORD, "doFreeze",
                new Object[]{FREEZE_REQUEST, boolean.class}, false);
    }

    private void installFreezeProcess() {
        installMethod(FREEZER_MANAGER, "freezeProcess",
                new Object[]{PROCESS_INFO}, null);
    }

    private void installQuickFreezer() {
        installMethod(APP_QUICK_FREEZER, "requestFreeze",
                new Object[]{int.class, String.class, String.class}, false);
        installMethod(APP_QUICK_FREEZER, "requestFreeze",
                new Object[]{int[].class, String[].class, String.class}, false);
    }

    private void installMethod(String className, String methodName, Object[] params, Object skipResult) {
        Class<?> clazz = CakeReflection.findClassIfExists(className, classLoader);
        if (clazz == null)
            return;
        if (CakeReflection.findMethodExactIfExists(clazz, methodName, params) == null)
            return;

        CakeHooker.Callback callback = new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback before) {
                if (!FreezeBackend.shouldHandleVivoEvents())
                    return;
                before.returnAndSkip(skipResult);
            }
        };

        try {
            Object[] args = Arrays.copyOf(params, params.length + 1);
            args[params.length] = callback;
            handles.add(CakeReflection.findAndHookMethod(clazz, methodName, args));
            Log.i(getSimpleName(className) + "." + methodName + " -> 成功Hook完毕! (vivo 冻结拦截)");
        } catch (Throwable throwable) {
            if (!isIgnoreError())
                Log.e(getSimpleName(className) + "." + methodName + " 冻结拦截 hook 失败", throwable);
        }
    }

    private static String getSimpleName(String className) {
        int index = className.lastIndexOf('.');
        return index < 0 ? className : className.substring(index + 1);
    }

    @Override
    public void unhook() {
        for (XposedInterface.HookHandle handle : handles) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
            }
        }
        handles.clear();
        hooked = false;
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
