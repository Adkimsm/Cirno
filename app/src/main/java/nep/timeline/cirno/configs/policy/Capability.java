package nep.timeline.cirno.configs.policy;

public enum Capability {
    BLACK_LIST(false),
    WHITE_LIST(false),
    ALLOW_BACKGROUND_AUDIO(true),
    ALLOW_LOCATION(true),
    ALLOW_NETWORK_MESSAGE(true),
    ALLOW_NETWORK_SPEED(true),
    ALLOW_ONGOING_NOTIFICATION(true),
    BLOCK_AUTOSTART(false);

    public final boolean isExemption;

    Capability(boolean isExemption) {
        this.isExemption = isExemption;
    }
}
