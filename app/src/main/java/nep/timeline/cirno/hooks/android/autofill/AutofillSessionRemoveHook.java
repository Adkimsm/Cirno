package nep.timeline.cirno.hooks.android.autofill;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.framework.MethodHook;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.reflect.CakeHooker;
import nep.timeline.cirno.reflect.CakeReflection;
import nep.timeline.cirno.threads.FreezerHandler;
import nep.timeline.cirno.utils.AutofillData;
import nep.timeline.cirno.utils.ReflectUtils;

public class AutofillSessionRemoveHook extends MethodHook {
    public AutofillSessionRemoveHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public String getTargetClass() {
        return "com.android.server.autofill.AutofillManagerServiceImpl";
    }

    @Override
    public String getTargetMethod() {
        return "removeSessionLocked";
    }

    @Override
    public Object[] getTargetParam() {
        return ReflectUtils.findParameterTypesOrDefault(
                CakeReflection.findClassIfExists(getTargetClass(), classLoader),
                getTargetMethod(),
                int.class);
    }

    @Override
    public CakeHooker.Callback getTargetHook() {
        return new CakeHooker.Callback() {
            @Override
            public void call(CakeHooker.AfterHookCallback callback) {
                try {
                    if (callback.throwable != null) {
                        Log.w("Autofill removeSession 原方法异常", callback.throwable);
                        return;
                    }

                    Object[] args = callback.getArgs();
                    if (args.length < 1 || !(args[0] instanceof Integer sessionId)) {
                        Log.w("Autofill removeSession 忽略，参数异常 args=" + args.length);
                        return;
                    }

                    int userId = CakeReflection.getIntField(callback.getThisObject(), "mUserId");
                    Log.d("Autofill removeSession 命中 sessionId=" + sessionId + " userId=" + userId);

                    AppRecord appRecord = AutofillData.removeSession(userId, sessionId);
                    if (appRecord == null) {
                        Log.d("Autofill removeSession 未找到记录 sessionId=" + sessionId + " userId=" + userId);
                        return;
                    }

                    Log.i("Autofill session 结束 sessionId=" + sessionId
                            + " app=" + appRecord.getPackageNameWithUser() + " activeSessions=" + AutofillData.getSessionCount());

                    if (AutofillData.hasActiveSession(appRecord)) {
                        Log.d("Autofill provider 保持豁免 app=" + appRecord.getPackageNameWithUser()
                                + " activeSessions=" + AutofillData.getActiveSessionCount(appRecord));
                        return;
                    }

                    if (appRecord.equals(AutofillData.currentAutofillApp)) {
                        AutofillData.currentAutofillApp = null;
                    }
                    Log.d("Autofill provider 结束豁免 app=" + appRecord.getPackageNameWithUser());
                    Log.d("Autofill provider 发送冻结 app=" + appRecord.getPackageNameWithUser());
                    FreezerHandler.sendFreezeMessage(appRecord);
                } catch (Throwable e) {
                    Log.e("AutofillSessionRemoveHook 处理失败", e);
                }
            }
        };
    }
}
