package nep.timeline.cirno.configs.policy;

import java.util.HashMap;
import java.util.Map;

public enum FreezeExemption {
    VISIBLE("VISIBLE", "\uD83D\uDC41 应用前台"),
    WHITELIST("WHITELIST", "\uD83E\uDEE1 白名单"),
    BLACKLIST("BLACKLIST", "\u26D4 黑名单"),
    INPUT("INPUT", "\u2328 输入法"),
    AUTOFILL("AUTOFILL", "自动填充服务"),
    CREDENTIAL("CREDENTIAL", "通行密钥服务"),
    ACCESSIBILITY("ACCESSIBILITY", "无障碍服务"),
    SYSTEM("SYSTEM", "\uD83D\uDD12 系统应用"),
    WAITING_PUSH_RESPONSE("WAITING_PUSH_RESPONSE", "\uD83D\uDCF0 等待推送响应"),
    AUDIO("AUDIO", "\uD83C\uDFB5 播放音频中"),
    LOCATION("LOCATION", "\uD83D\uDCCD 定位中"),
    RECORDING("RECORDING", "\uD83C\uDF99\uFE0F 录音中"),
    CAMERA("CAMERA", "使用摄像头中"),
    OVERLAY("OVERLAY", "🪟 显示悬浮窗中"),
    VPN("VPN", "\uD83C\uDF10 使用VPN服务中"),
    NETWORK_SPEED("NETWORK_SPEED", "\uD83D\uDEDC 网速传输中"),
    WAITING_FROZEN("WAITING_FROZEN", "\u23F3 等待冻结"),
    UNKNOWN("UNKNOWN", "未知");

    public final String reason;
    public final String displayText;

    private static final Map<String, FreezeExemption> BY_REASON = new HashMap<>();

    static {
        for (FreezeExemption exemption : values()) {
            BY_REASON.put(exemption.reason, exemption);
        }
    }

    FreezeExemption(String reason, String displayText) {
        this.reason = reason;
        this.displayText = displayText;
    }

    public static FreezeExemption fromReason(String reason) {
        if (reason == null) {
            return UNKNOWN;
        }
        FreezeExemption exemption = BY_REASON.get(reason);
        return exemption != null ? exemption : UNKNOWN;
    }
}
