package nep.timeline.cirno.hooks.android.intent;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.entity.PendingIntentKey;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.ReflectUtils;

public class PendingIntentHook extends MethodHook {
    public PendingIntentHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.PendingIntentRecord";
    }

    @Override
    public String getTargetMethod() {
        return "sendInner";
    }

    @Override
    public Object[] getTargetParam() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU)
            return ReflectUtils.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), "android.app.IApplicationThread", int.class, Intent.class, String.class, IBinder.class, "android.content.IIntentReceiver", String.class, IBinder.class, String.class, int.class, int.class, int.class, Bundle.class);
        return ReflectUtils.findParameterTypesOrDefault(CakeReflection.findClassIfExists(getTargetClass(), classLoader), getTargetMethod(), int.class, Intent.class, String.class, IBinder.class, "android.content.IIntentReceiver", String.class, IBinder.class, String.class, int.class, int.class, int.class, Bundle.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.BeforeHookCallback callback) {
                // sendInner 是全系统 PendingIntent 发送热路径（闹钟/通知点击/widget）。
                // 不再抢占 PendingIntentController.mLock：key 是 final 字段，canceled 的
                // 无锁读最多造成一次多余的解冻检查；旧实现在持 mLock 期间执行解冻
                // （cgroup 写 I/O + handler 操作）会放大锁争用造成系统级卡顿
                Object record = callback.getThisObject();
                if (CakeReflection.getBooleanField(record, "canceled"))
                    return;

                Object key = CakeReflection.getObjectField(record, "key");
                if (key == null)
                    return;

                PendingIntentKey pendingIntentKey = new PendingIntentKey(key);

                AppRecord appRecord = AppService.get(pendingIntentKey.getPackageName(), pendingIntentKey.getUserId());

                if (appRecord == null || !appRecord.isFrozen())
                    return;

                FreezerService.temporaryUnfreezeIfNeed(appRecord, "Intent", 3000);
            }
        };
    }
}
