package nep.timeline.cirno.hooks.android.location;

import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Binder;

import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.handlers.LocationHandler;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.PKGUtils;
import nep.timeline.cirno.virtuals.ILocationListener;

public class ListenerRegisterHook extends MethodHook {
    public ListenerRegisterHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.location.LocationManagerService";
    }

    @Override
    public String getTargetMethod() {
        return "registerLocationListener";
    }

    @Override
    public Object[] getTargetParam() {
        return new Object[]{String.class, LocationRequest.class, "android.location.ILocationListener", String.class, String.class, String.class};
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                boolean isGPS = LocationManager.GPS_PROVIDER.equals(callback.getArgs()[0]);
                if (!isGPS)
                    return;

                String packageName = (String) callback.getArgs()[3];
                int uid = Binder.getCallingUid();

                ILocationListener listener = new ILocationListener(callback.getArgs()[2]);

                Handlers.location.post(() -> {
                    AppRecord appRecord = AppService.get(packageName, PKGUtils.getUserId(uid));

                    if (appRecord == null)
                        return;

                    android.os.IBinder binder = listener.asBinder();
                    if (appRecord.getAppState().addLocationListener(binder)) {
                        // LocationManagerService 在客户端 binder 死亡时走内部清理，不会经过被
                        // hook 的 unregisterLocationListener：必须 linkToDeath 兜底移除，
                        // 否则应用异常退出后 location 状态永久为 true，该应用将永远不被冻结
                        try {
                            binder.linkToDeath(() -> Handlers.location.post(() -> {
                                if (appRecord.getAppState().removeLocationListener(binder))
                                    LocationHandler.call(appRecord);
                            }), 0);
                        } catch (Throwable ignored) {
                            // 注册时 binder 已死亡：直接回滚
                            appRecord.getAppState().removeLocationListener(binder);
                            return;
                        }
                        LocationHandler.call(appRecord);
                    }
                });
            }
        };
    }
}
