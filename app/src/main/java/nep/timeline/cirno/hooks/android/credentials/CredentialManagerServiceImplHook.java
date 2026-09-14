package nep.timeline.cirno.hooks.android.credentials;

import android.content.pm.ServiceInfo;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.utils.CredentialData;
import nep.timeline.cirno.utils.ReflectUtils;

public class CredentialManagerServiceImplHook extends MethodHook {

    public CredentialManagerServiceImplHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public int getMinVersion() {
        return 34; // UPSIDE_DOWN_CAKE
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.credentials.CredentialManagerServiceImpl";
    }

    @Override
    public String getTargetMethod() {
        return "initiateProviderSessionForRequestLocked";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(),
                "com.android.server.credentials.RequestSession",
                java.util.List.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    if (callback.throwable != null) {
                        Log.w("Credential initiateProviderSession 原方法异常", callback.throwable);
                        return;
                    }

                    Object providerSession = callback.result;
                    if (providerSession == null) {
                        Log.d("Credential initiateProviderSession 忽略，返回值为null");
                        return;
                    }

                    Object infoObject = CakeReflection.getObjectField(callback.getThisObject(), "mInfo");
                    if (infoObject == null) {
                        Log.w("Credential provider 解析失败: mInfo is null");
                        return;
                    }

                    Object serviceInfoObject = CakeReflection.callMethod(infoObject, "getServiceInfo");
                    if (!(serviceInfoObject instanceof ServiceInfo serviceInfo)) {
                        Log.w("Credential provider 解析失败: getServiceInfo returned " + serviceInfoObject);
                        return;
                    }

                    if (serviceInfo == null || serviceInfo.packageName == null || serviceInfo.packageName.isEmpty()) {
                        Log.w("Credential provider 解析失败: packageName empty");
                        return;
                    }

                    int userId = CakeReflection.getIntField(callback.getThisObject(), "mUserId");
                    AppRecord appRecord = AppService.get(serviceInfo.packageName, userId);
                    if (appRecord == null) {
                        Log.w("Credential provider 未找到 AppRecord pkg=" + serviceInfo.packageName + " userId=" + userId);
                        return;
                    }

                    boolean newSession = CredentialData.putSession(userId, providerSession, appRecord);
                    Log.i("Credential provider session " + (newSession ? "开始" : "更新")
                            + " app=" + appRecord.getPackageNameWithUser()
                            + " activeSessions=" + CredentialData.getSessionCount());

                    FreezerService.thaw(appRecord);
                    Log.i("Credential provider 解冻 app=" + appRecord.getPackageNameWithUser());
                } catch (Throwable e) {
                    Log.e("CredentialManagerServiceImpl 处理失败", e);
                }
            }
        };
    }
}
