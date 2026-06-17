package calebxzhou.rdi.mc.client.gui;

import java.util.UUID;

public record RdiTabRow(String text, UUID playerId, int color) {
    public boolean isPlayer() {
        return playerId != null;
    }
}
