package nep.timeline.cirno.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import java.io.File;
import java.io.IOException;

import nep.timeline.cirno.GlobalVars;
import nep.timeline.cirno.configs.settings.ApplicationSettings;
import nep.timeline.cirno.configs.settings.GlobalSettings;
import nep.timeline.cirno.log.Log;
import nep.timeline.cirno.utils.RWUtils;

public class ConfigManagerJson {
    public enum ReadResult {
        OK,
        MISSING,
        READ_FAILED,
        INVALID_JSON
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final String globalSettingsName = "GlobalSettings.json";
    private final String applicationSettingsName = "ApplicationSettings.json";

    private void ensureApplicationSettingsInitialized() {
        GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(GlobalVars.applicationSettings);
    }

    private boolean fileExists(File file, boolean su) {
        return su ? new SuFile(file.getAbsolutePath()).exists() : file.exists();
    }

    private void resetToDefaults() {
        GlobalVars.globalSettings = new GlobalSettings();
        GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(null);
    }

    private void prepareConfigDirSU() {
        Shell.cmd(
                "mkdir -p " + GlobalVars.CONFIG_DIR,
                "mkdir -p " + GlobalVars.LOG_DIR,
                "chown system:system " + GlobalVars.CONFIG_DIR + " " + GlobalVars.LOG_DIR,
                "chmod 0770 " + GlobalVars.CONFIG_DIR + " " + GlobalVars.LOG_DIR,
                "[ ! -e " + GlobalVars.CONFIG_DIR + "/" + globalSettingsName + " ] || chown system:system " + GlobalVars.CONFIG_DIR + "/" + globalSettingsName,
                "[ ! -e " + GlobalVars.CONFIG_DIR + "/" + globalSettingsName + " ] || chmod 0660 " + GlobalVars.CONFIG_DIR + "/" + globalSettingsName,
                "[ ! -e " + GlobalVars.CONFIG_DIR + "/" + applicationSettingsName + " ] || chown system:system " + GlobalVars.CONFIG_DIR + "/" + applicationSettingsName,
                "[ ! -e " + GlobalVars.CONFIG_DIR + "/" + applicationSettingsName + " ] || chmod 0660 " + GlobalVars.CONFIG_DIR + "/" + applicationSettingsName,
                "[ ! -e " + GlobalVars.LOG_DIR + "/current.log ] || chown system:system " + GlobalVars.LOG_DIR + "/current.log",
                "[ ! -e " + GlobalVars.LOG_DIR + "/current.log ] || chmod 0660 " + GlobalVars.LOG_DIR + "/current.log",
                "[ ! -e " + GlobalVars.LOG_DIR + "/last.log ] || chown system:system " + GlobalVars.LOG_DIR + "/last.log",
                "[ ! -e " + GlobalVars.LOG_DIR + "/last.log ] || chmod 0660 " + GlobalVars.LOG_DIR + "/last.log",
                "restorecon -R " + GlobalVars.CONFIG_DIR + " >/dev/null 2>&1 || true"
        ).exec();
    }

    private void prepareLogDirSU() {
        Shell.cmd(
                "mkdir -p " + GlobalVars.LOG_DIR,
                "chown system:system " + GlobalVars.CONFIG_DIR + " " + GlobalVars.LOG_DIR,
                "chmod 0770 " + GlobalVars.CONFIG_DIR + " " + GlobalVars.LOG_DIR,
                "[ ! -e " + GlobalVars.LOG_DIR + "/current.log ] || chown system:system " + GlobalVars.LOG_DIR + "/current.log",
                "[ ! -e " + GlobalVars.LOG_DIR + "/current.log ] || chmod 0660 " + GlobalVars.LOG_DIR + "/current.log",
                "[ ! -e " + GlobalVars.LOG_DIR + "/last.log ] || chown system:system " + GlobalVars.LOG_DIR + "/last.log",
                "[ ! -e " + GlobalVars.LOG_DIR + "/last.log ] || chmod 0660 " + GlobalVars.LOG_DIR + "/last.log",
                "restorecon -R " + GlobalVars.CONFIG_DIR + " >/dev/null 2>&1 || true"
        ).exec();
    }

    private void writeConfigByMode(boolean su) {
        try {
            if (su) {
                SuFile configDir = new SuFile(GlobalVars.CONFIG_DIR);
                if (!configDir.exists()) {
                    configDir.mkdir();
                }
                SuFile logDir = new SuFile(GlobalVars.LOG_DIR);
                if (!logDir.exists()) {
                    logDir.mkdir();
                }
                prepareConfigDirSU();
            } else {
                File configDir = new File(GlobalVars.CONFIG_DIR);
                if (!configDir.exists()) {
                    configDir.mkdir();
                }
                File logDir = new File(GlobalVars.LOG_DIR);
                if (!logDir.exists()) {
                    logDir.mkdir();
                }
            }

            GlobalSettings globalSettings = GlobalVars.globalSettings;
            if (globalSettings != null) {
                String globalConfigStr = gson.toJson(globalSettings);
                if (su) {
                    RWUtils.writeStringToFileSU(new SuFile(GlobalVars.CONFIG_DIR, globalSettingsName), globalConfigStr, false);
                } else {
                    RWUtils.writeStringToFile(new File(GlobalVars.CONFIG_DIR, globalSettingsName), globalConfigStr);
                }
            }

            ApplicationSettings applicationSettings = GlobalVars.applicationSettings;
            if (applicationSettings != null) {
                String applicationConfigStr = gson.toJson(applicationSettings);
                if (su) {
                    RWUtils.writeStringToFileSU(new SuFile(GlobalVars.CONFIG_DIR, applicationSettingsName), applicationConfigStr, false);
                } else {
                    RWUtils.writeStringToFile(new File(GlobalVars.CONFIG_DIR, applicationSettingsName), applicationConfigStr);
                }
            }

            if (su) {
                prepareConfigDirSU();
            }
        } catch (IOException e) {
            Log.e("Save Config", e);
        }
    }

    private ReadResult readGlobalConfig(File globalFile, boolean su) {
        if (!fileExists(globalFile, su)) {
            // 文件缺失时保留内存中的现有配置（UI 保存/备份恢复常用"删除后重写"，
            // 中间窗口触发的读取不能把用户配置重置为默认值），仅首次启动时才建默认配置
            GlobalVars.globalSettings = GlobalSettings.ensureInitialized(GlobalVars.globalSettings);
            return ReadResult.MISSING;
        }

        String globalData = su ? RWUtils.readConfig(new SuFile(globalFile.getAbsolutePath())) : RWUtils.readConfig(globalFile.getAbsolutePath());
        if (globalData == null) {
            Log.w("Read Config", new IOException("Failed to read " + globalFile.getAbsolutePath()));
            return ReadResult.READ_FAILED;
        }

        try {
            GlobalVars.globalSettings = GlobalSettings.ensureInitialized(gson.fromJson(globalData, GlobalSettings.class));
            return ReadResult.OK;
        } catch (JsonSyntaxException | JsonIOException e) {
            Log.e("Read Config", e);
            return ReadResult.INVALID_JSON;
        }
    }

    private ReadResult readApplicationConfig(File applicationFile, boolean su) {
        if (!fileExists(applicationFile, su)) {
            // 同上：文件缺失不能清空用户的黑白名单等内存配置，
            // 之后的 saveConfig() 会把保留下来的配置重新落盘（相当于自动恢复）
            GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(GlobalVars.applicationSettings);
            return ReadResult.MISSING;
        }

        String applicationData = su ? RWUtils.readConfig(new SuFile(applicationFile.getAbsolutePath())) : RWUtils.readConfig(applicationFile.getAbsolutePath());
        if (applicationData == null) {
            Log.w("Read Config", new IOException("Failed to read " + applicationFile.getAbsolutePath()));
            return ReadResult.READ_FAILED;
        }

        try {
            GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(gson.fromJson(applicationData, ApplicationSettings.class));
            return ReadResult.OK;
        } catch (JsonSyntaxException | JsonIOException e) {
            Log.e("Read Config", e);
            return ReadResult.INVALID_JSON;
        }
    }

    private ReadResult mergeReadResults(ReadResult first, ReadResult second) {
        if (first == ReadResult.READ_FAILED || second == ReadResult.READ_FAILED) {
            return ReadResult.READ_FAILED;
        }
        if (first == ReadResult.INVALID_JSON || second == ReadResult.INVALID_JSON) {
            return ReadResult.INVALID_JSON;
        }
        if (first == ReadResult.MISSING || second == ReadResult.MISSING) {
            return ReadResult.MISSING;
        }
        return ReadResult.OK;
    }

    public ReadResult readConfig() {
        File globalFile = new File(GlobalVars.CONFIG_DIR, globalSettingsName);
        File applicationFile = new File(GlobalVars.CONFIG_DIR, applicationSettingsName);

        ReadResult globalResult = readGlobalConfig(globalFile, false);
        ReadResult applicationResult = readApplicationConfig(applicationFile, false);

        ensureApplicationSettingsInitialized();

        ReadResult result = mergeReadResults(globalResult, applicationResult);
        GlobalVars.globalSettings = GlobalSettings.ensureInitialized(GlobalVars.globalSettings);
        if (result == ReadResult.MISSING) {
            saveConfig();
        }
        return result;
    }

    public void saveConfig() {
        writeConfigByMode(false);
    }

    public ReadResult readConfigSU() {
        ReadResult globalResult = readGlobalConfig(new File(GlobalVars.CONFIG_DIR, globalSettingsName), true);
        ReadResult applicationResult = readApplicationConfig(new File(GlobalVars.CONFIG_DIR, applicationSettingsName), true);
        ensureApplicationSettingsInitialized();

        ReadResult result = mergeReadResults(globalResult, applicationResult);
        GlobalVars.globalSettings = GlobalSettings.ensureInitialized(GlobalVars.globalSettings);
        if (result == ReadResult.MISSING) {
            saveConfigSU();
        }
        return result;
    }

    public void saveConfigSU() {
        writeConfigByMode(true);
    }

    public String dumpGlobalSettingsJson() {
        GlobalSettings settings = GlobalSettings.ensureInitialized(GlobalVars.globalSettings);
        GlobalVars.globalSettings = settings;
        return gson.toJson(settings);
    }

    public String dumpApplicationSettingsJson() {
        ApplicationSettings settings = ApplicationSettings.ensureInitialized(GlobalVars.applicationSettings);
        GlobalVars.applicationSettings = settings;
        return gson.toJson(settings);
    }

    public boolean applyGlobalSettingsJson(String json) {
        GlobalSettings oldSettings = GlobalVars.globalSettings;
        try {
            GlobalSettings settings = gson.fromJson(json, GlobalSettings.class);
            GlobalVars.globalSettings = GlobalSettings.ensureInitialized(settings);
            saveConfig();
            return true;
        } catch (Throwable e) {
            GlobalVars.globalSettings = oldSettings;
            Log.e("Apply GlobalSettings", e);
            return false;
        }
    }

    public boolean applyGlobalSettingsJsonSU(String json) {
        GlobalSettings oldSettings = GlobalVars.globalSettings;
        try {
            GlobalSettings settings = gson.fromJson(json, GlobalSettings.class);
            GlobalVars.globalSettings = GlobalSettings.ensureInitialized(settings);
            saveConfigSU();
            return true;
        } catch (Throwable e) {
            GlobalVars.globalSettings = oldSettings;
            Log.e("Apply GlobalSettings", e);
            return false;
        }
    }

    public boolean applyApplicationSettingsJson(String json) {
        ApplicationSettings oldSettings = GlobalVars.applicationSettings;
        try {
            ApplicationSettings settings = gson.fromJson(json, ApplicationSettings.class);
            GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(settings);
            saveConfig();
            return true;
        } catch (Throwable e) {
            GlobalVars.applicationSettings = oldSettings;
            Log.e("Apply ApplicationSettings", e);
            return false;
        }
    }

    public boolean applyApplicationSettingsJsonSU(String json) {
        ApplicationSettings oldSettings = GlobalVars.applicationSettings;
        try {
            ApplicationSettings settings = gson.fromJson(json, ApplicationSettings.class);
            GlobalVars.applicationSettings = ApplicationSettings.ensureInitialized(settings);
            saveConfigSU();
            return true;
        } catch (Throwable e) {
            GlobalVars.applicationSettings = oldSettings;
            Log.e("Apply ApplicationSettings", e);
            return false;
        }
    }
}
