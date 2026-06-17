package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;

import java.util.List;

public final class GlobalPlayerListState {
    private static volatile RGlobalPlayerList current = new RGlobalPlayerList(0L, List.of());

    private GlobalPlayerListState() {
    }

    public static RGlobalPlayerList current() {
        return current;
    }

    public static void update(RGlobalPlayerList playerList) {
        if (playerList != null) {
            current = playerList;
        }
    }
}
