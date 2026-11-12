package nep.timeline.cirno.hooks.android.vivo;

import nep.timeline.cirno.framework.HookTarget;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.utils.SystemChecker;

/**
 * 从源头拒绝 vivo 的冻结请求。
 *
 * <p>进程被冻结后无法处理 binder 事务，冻结成功时 vivo 还会主动关闭 socket、回收
 * WiFi/GPS/Sensor/Wakelock/Alarm/Job 等资源，事后靠 BBL/BFL 或网络事件解冻必然存在延迟。
 * 直接拒绝冻结可以让进程始终可调度，binder 与网络保持原样。
 *
 * <p>这是 vivo 设备上的常驻行为，与冻结后端（hookType）无关：只要存在下列 vivo 类就会安装。
 * 切换 Re-Kernel/nkBinder 时只卸载事件 hook，本 hook 保持生效。
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

    public VivoFreezeDenyHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    protected HookTarget[] getTargets() {
        CakeHooker.Callback skipFalse = skipCallback(false);
        return new HookTarget[] {
                HookTarget.method(FREEZE_RECORD, "doFreeze",
                        new Object[]{FREEZE_REQUEST, boolean.class}, skipFalse),
                HookTarget.method(FREEZER_MANAGER, "freezeProcess",
                        new Object[]{PROCESS_INFO}, skipCallback(null)),
                HookTarget.method(APP_QUICK_FREEZER, "requestFreeze",
                        new Object[]{int.class, String.class, String.class}, skipFalse),
                HookTarget.method(APP_QUICK_FREEZER, "requestFreeze",
                        new Object[]{int[].class, String[].class, String.class}, skipFalse)
        };
    }

    private static CakeHooker.Callback skipCallback(Object result) {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                callback.returnAndSkip(result);
            }
        };
    }

    @Override
    public boolean isIgnoreError() {
        return !SystemChecker.isVivo(classLoader);
    }
}
