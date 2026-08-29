package nep.timeline.cirno.log;

import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import nep.timeline.cirno.BuildConfig;
import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.services.StatusBinderHub;
import nep.timeline.cirno.threads.Handlers;
import nep.timeline.cirno.utils.RWUtils;

public class Log {
    private static final String SIGNAL_FILE_LOG_ERROR = "file_log_error";
    private final static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN);
    private final static File currentLog = new File(GlobalVars.LOG_DIR, "current.log");

    static {
        i("当前Cirno版本: v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "-" + BuildConfig.BUILD_TIME + ")");
        i("设备Android SDK: " + Build.VERSION.SDK_INT);
    }

    static {
        int freezeDelay = 5;
        if (GlobalVars.globalSettings != null) {
            freezeDelay = GlobalVars.globalSettings.freezeDelay;
        }
        i("冻结延时: " + 1000 * freezeDelay + "ms");
    }

    public static void d(String msg) {
        execute("调试", msg);
    }

    public static void d(String msg, Throwable throwable) {
        throwable = unwrap(throwable);
        String detail = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            detail += ": " + message;
        }
        d(msg + " 失败: " + detail);
    }

    public static void i(String msg) {
        execute("信息", msg);
    }

    public static void w(String msg) {
        execute("警告", msg);
    }

    public static void w(String msg, Throwable throwable) {
        throwable = unwrap(throwable);
        String detail = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            detail += ": " + message;
        }
        w(msg + " 失败: " + detail);
    }

    public static void e(String msg) {
        execute("错误", msg);
    }

    public static void e(String msg, Throwable throwable) {
        throwable = unwrap(throwable);
        String detail = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            detail += ": " + message;
        }
        e(msg + " 失败: " + detail);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.lang.reflect.InvocationTargetException
                || current instanceof nep.timeline.cirno.reflect.CakeReflection.InvocationTargetError) {
            Throwable cause = current instanceof java.lang.reflect.InvocationTargetException
                    ? ((java.lang.reflect.InvocationTargetException) current).getTargetException()
                    : current.getCause();
            if (cause == null || cause == current) break;
            current = cause;
        }
        return current;
    }

    private static String getLogLevel() {
        if (GlobalVars.globalSettings == null || GlobalVars.globalSettings.logLevel == null) {
            return GlobalSettings.LOG_LEVEL_INFO;
        }
        return GlobalVars.globalSettings.logLevel;
    }

    private static boolean shouldLog(String level) {
        String logLevel = getLogLevel();
        if (GlobalSettings.LOG_LEVEL_DEBUG.equals(logLevel)) {
            return "信息".equals(level) || "调试".equals(level);
        }
        if (GlobalSettings.LOG_LEVEL_INFO.equals(logLevel)) {
            return "信息".equals(level);
        }
        return false;
    }

    public static void execute(String level, String msg) {
        // 级别过滤前置：被丢弃的 debug/info 日志不再在调用线程（往往是 system_server 热路径）
        // 做时间格式化与字符串拼接，也不再唤醒 Log 线程
        boolean isError = "错误".equals(level);
        boolean isWarning = "警告".equals(level);
        if (!isError && !isWarning && !shouldLog(level)) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        Handlers.log.post(() -> {
            // SimpleDateFormat 非线程安全，仅在 Log 单线程内使用
            String formatted = simpleDateFormat.format(new Date(timestamp)) + " " + level.toUpperCase() + " -> " + msg;
            if (isError) {
                StatusBinderHub.signalError();
            }
            fileLog(formatted);
        });
    }

    public static void fileLog(String msg) {
        try {
            RWUtils.writeStringToFile(currentLog, msg, true);
            StatusBinderHub.setSignal(SIGNAL_FILE_LOG_ERROR, "");
        } catch (IOException e) {
            StatusBinderHub.setSignal(SIGNAL_FILE_LOG_ERROR, formatFileLogError(e));
        }
    }

    private static String formatFileLogError(IOException e) {
        String detail = e.getClass().getSimpleName();
        String message = e.getMessage();
        if (message != null && !message.isEmpty()) {
            detail += ": " + message;
        }
        return detail + " (" + currentLog.getAbsolutePath() + ")";
    }

}
