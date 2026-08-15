package nep.timeline.cirno.hooks.android.autofill;

import android.content.pm.ServiceInfo;
import android.os.Build;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.services.AppService;
import nep.timeline.cirno.services.FreezerService;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.utils.AutofillData;
import nep.timeline.cirno.utils.ReflectUtils;

public class AutofillManagerServiceImplHook extends MethodHook {
    private static final long INVALID_SESSION_ID = 0L;
    private static final long FAILED_SESSION_ID = 0x7fffffffL;

    public AutofillManagerServiceImplHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.autofill.AutofillManagerServiceImpl";
    }

    @Override
    public String getTargetMethod() {
        return "startSessionLocked";
    }

    @Override
    public Object[] getTargetParam() {
        Class<?> targetClass = CakeReflection.findClassIfExists(getTargetClass(), classLoader);
        if (Build.VERSION.SDK_INT >= 37) {
            return ReflectUtils.findParameterTypesOrDefault(
                    targetClass,
                    getTargetMethod(),
                    android.os.IBinder.class,
                    int.class,
                    int.class,
                    android.os.IBinder.class,
                    android.view.autofill.AutofillId.class,
                    android.graphics.Rect.class,
                    android.view.autofill.AutofillValue.class,
                    boolean.class,
                    android.content.ComponentName.class,
                    boolean.class,
                    boolean.class,
                    String.class,
                    int.class);
        }

        return ReflectUtils.findParameterTypesOrDefault(
                targetClass,
                getTargetMethod(),
                android.os.IBinder.class,
                int.class,
                int.class,
                android.os.IBinder.class,
                android.view.autofill.AutofillId.class,
                android.graphics.Rect.class,
                android.view.autofill.AutofillValue.class,
                boolean.class,
                android.content.ComponentName.class,
                boolean.class,
                boolean.class,
                int.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    AutofillData.instance = callback.getThisObject();

                    if (callback.throwable != null) {
                        Log.w("Autofill startSession 原方法异常", callback.throwable);
                        return;
                    }

                    Object result = callback.result;
                    if (!(result instanceof Long sessionResult)) {
                        Log.w("Autofill startSession 忽略，返回值类型异常 result=" + result);
                        return;
                    }

                    int userId = CakeReflection.getIntField(callback.getThisObject(), "mUserId");
                    int sessionId = (int) (sessionResult & 0xffffffffL);
                    Log.d("Autofill startSession 命中 sessionId=" + sessionId
                            + " result=" + sessionResult
                            + " userId=" + userId
                            + " args=" + callback.getArgs().length);

                    if (sessionId == 0 || sessionId == FAILED_SESSION_ID) {
                        Log.d("Autofill startSession 忽略，无效 sessionId=" + sessionId + " userId=" + userId);
                        return;
                    }

                    Object infoObject = CakeReflection.getObjectField(callback.getThisObject(), "mInfo");
                    if (infoObject == null) {
                        Log.w("Autofill provider 解析失败: mInfo is null, userId=" + userId);
                        return;
                    }

                    Object serviceInfoObject = CakeReflection.callMethod(infoObject, "getServiceInfo");
                    if (!(serviceInfoObject instanceof ServiceInfo serviceInfo)) {
                        Log.w("Autofill provider 解析失败: getServiceInfo returned " + serviceInfoObject + ", userId=" + userId);
                        return;
                    }

                    if (serviceInfo == null || serviceInfo.packageName == null || serviceInfo.packageName.isEmpty()) {
                        Log.w("Autofill provider 解析失败: packageName empty, userId=" + userId);
                        return;
                    }

                    AppRecord appRecord = AppService.get(serviceInfo.packageName, userId);
                    if (appRecord == null) {
                        Log.w("Autofill provider 未找到 AppRecord pkg=" + serviceInfo.packageName + " userId=" + userId);
                        return;
                    }

                    boolean newSession = AutofillData.putSession(userId, sessionId, appRecord);
                    Log.i("Autofill session " + (newSession ? "开始" : "更新") + " sessionId=" + sessionId
                            + " app=" + appRecord.getPackageNameWithUser() + " activeSessions=" + AutofillData.getSessionCount());

                    if (appRecord.equals(AutofillData.currentAutofillApp)) {
                        Log.d("Autofill provider 未变化 app=" + appRecord.getPackageNameWithUser());
                        return;
                    }

                    AppRecord oldApp = AutofillData.currentAutofillApp;
                    AutofillData.currentAutofillApp = appRecord;
                    Log.i("Autofill provider 切换 old=" + formatApp(oldApp) + " new=" + appRecord.getPackageNameWithUser());
                    Log.i("Autofill provider 解冻 app=" + appRecord.getPackageNameWithUser());
                    FreezerService.thaw(appRecord);
                    if (oldApp != null && !AutofillData.hasActiveSession(oldApp)) {
                        Log.i("Autofill provider 回收旧豁免 app=" + oldApp.getPackageNameWithUser());
                        FreezerHandler.sendFreezeMessage(oldApp);
                    }
                } catch (Throwable e) {
                    Log.e("AutofillManagerServiceImpl 处理失败", e);
                }
            }
        };
    }

    private static String formatApp(AppRecord appRecord) {
        return appRecord == null ? "null" : appRecord.getPackageNameWithUser();
    }
}
