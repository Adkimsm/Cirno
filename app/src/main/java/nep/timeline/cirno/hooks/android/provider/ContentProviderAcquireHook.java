package nep.timeline.cirno.hooks.android.provider;

import android.content.pm.ProviderInfo;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.ReflectUtils;

public class ContentProviderAcquireHook extends MethodHook {
    private static final long TEMP_UNFREEZE_INTERVAL_MS = 3000L;

    public ContentProviderAcquireHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.am.ContentProviderHelper";
    }

    @Override
    public String getTargetMethod() {
        return "checkAssociationAndPermissionLocked";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(),
                "com.android.server.am.ProcessRecord",
                ProviderInfo.class,
                int.class,
                int.class,
                boolean.class,
                String.class,
                long.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                if (callback.throwable != null)
                    return;

                try {
                    Object[] args = callback.getArgs();
                    if (!(args[1] instanceof ProviderInfo providerInfo)
                            || providerInfo.applicationInfo == null)
                        return;

                    int callingUid = (int) args[2];
                    int targetUid = providerInfo.applicationInfo.uid;
                    if (callingUid < 0 || targetUid < 0 || callingUid == targetUid)
                        return;

                    String authority = providerInfo.authority;
                    if (authority == null || authority.isEmpty())
                        authority = (String) args[5];

                    for (AppRecord appRecord : AppService.getByUid(targetUid)) {
                        if (appRecord == null || !appRecord.isFrozen())
                            continue;

                        FreezerService.temporaryUnfreezeIfNeed(
                                appRecord,
                                "ContentProvider " + authority + ", from_uid=" + callingUid,
                                TEMP_UNFREEZE_INTERVAL_MS);
                    }
                } catch (Throwable throwable) {
                    Log.e("ContentProvider 获取处理失败", throwable);
                }
            }
        };
    }
}
