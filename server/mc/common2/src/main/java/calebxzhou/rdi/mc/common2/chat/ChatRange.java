package calebxzhou.rdi.mc.common2.chat;

import java.util.Locale;

public enum ChatRange {
    HOST("本房间"),
    GLOBAL("公共");

    private final String displayName;

    ChatRange(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ChatRange fromRcmdValue(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "global" -> GLOBAL;
            default -> HOST;
        };
    }
}
