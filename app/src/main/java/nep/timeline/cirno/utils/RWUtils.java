package nep.timeline.cirno.utils;

import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nep.timeline.cirno.entity.AppRecord;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.services.AppService;

public class RWUtils {
    public static String readConfig(SuFile file) {
        try {
            return IOUtils.toString(() -> SuFileInputStream.open(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e("Read Config", e);
        }

        return null;
    }

    public static String readConfig(String name) {
        try {
            return String.join("\n", FileUtils.readLines(new File(name), StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.e("Read Config", e);
        }

        return null;
    }

    public static void writeStringToFile(File file, String value) throws IOException {
        writeStringToFile(file, value + "\n", false);
    }

    public static void writeStringToFile(File file, String value, boolean append) throws IOException {
        FileUtils.write(file, value + "\n", StandardCharsets.UTF_8, append);
    }

    public static void writeStringToFileSU(SuFile file, String value, boolean append) throws IOException {
        // 旧实现 new PrintWriter(stream, append) 的第二个参数其实是 autoFlush 而非 append，
        // 且 PrintWriter 会吞掉所有 IOException，SU 写失败时上层仍然以为保存成功
        try (java.io.OutputStream outputStream = SuFileOutputStream.open(file, append)) {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    public static boolean writeFrozen(String path, int value) {
        return writeFrozen(path, value, true);
    }

    private static final Pattern UID_PATTERN = Pattern.compile("uid_(\\d+)");

    public static boolean writeFrozen(String path, int value, boolean logFailure) {
        try (FileOutputStream outputStream = new FileOutputStream(path)) {
            outputStream.write(Integer.toString(value).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return true;
        } catch (IOException e) {
            if (!logFailure)
                return false;

            String message = e.getMessage();
            boolean processGone = message != null && (message.contains("ESRCH") || message.contains("No such process"));

            String label = "";
            Matcher m = UID_PATTERN.matcher(path);
            if (m.find()) {
                int uid = Integer.parseInt(m.group(1));
                List<AppRecord> records = AppService.getByUid(uid);
                if (!records.isEmpty()) {
                    label = " [" + records.get(0).getPackageNameWithUser() + "]";
                }
            }
            if (processGone) {
                Log.w(path + " | 进程已不存在，跳过冻结状态写入" + label + ", value=" + value, e);
                return false;
            }
            Log.w(path + " | 写入冻结状态失败" + label + ", 请检查cgroup v2支持、路径或权限", e);
            return false;
        }
    }
}
