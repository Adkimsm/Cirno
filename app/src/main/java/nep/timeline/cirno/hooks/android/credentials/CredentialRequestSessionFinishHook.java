package nep.timeline.cirno.hooks.android.credentials;

import java.util.Collection;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.utils.CredentialData;
import nep.timeline.cirno.utils.ReflectUtils;

public class CredentialRequestSessionFinishHook extends MethodHook {

    public CredentialRequestSessionFinishHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public int getMinVersion() {
        return 34; // UPSIDE_DOWN_CAKE
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.credentials.RequestSession";
    }

    @Override
    public String getTargetMethod() {
        return "finishSession";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(),
                boolean.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            @SuppressWarnings("unchecked")
            public void call(CakeHooker.BeforeHookCallback callback) {
                try {
                    Object thisObj = callback.getThisObject();
                    if (thisObj == null) {
                        return;
                    }

                    int userId = CakeReflection.getIntField(thisObj, "mUserId");
                    Object providersObj = CakeReflection.getObjectField(thisObj, "mProviders");
                    if (!(providersObj instanceof java.util.Map<?, ?> providers)) {
                        return;
                    }

                    Collection<?> sessions = providers.values();
                    for (Object providerSession : sessions) {
                        AppRecord appRecord = CredentialData.removeSession(userId, providerSession);
                        if (appRecord == null) {
                            continue;
                        }

                        if (!CredentialData.hasActiveSession(appRecord)) {
                            Log.i("Credential provider 发送冻结 app=" + appRecord.getPackageNameWithUser());
                            FreezerHandler.sendFreezeMessage(appRecord);
                        }
                    }
                } catch (Throwable e) {
                    Log.e("CredentialRequestSessionFinishHook 处理失败", e);
                }
            }
        };
    }
}
