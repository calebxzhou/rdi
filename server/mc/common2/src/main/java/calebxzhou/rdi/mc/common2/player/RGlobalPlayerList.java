package calebxzhou.rdi.mc.common2.player;

import java.util.List;


public record RGlobalPlayerList(long generatedAt, List<HostEntry> hosts) {
    public RGlobalPlayerList {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
    }

    public record HostEntry(
            String hostId,
            String hostName,
            String modpackName,
            String packVer,
            List<PlayerEntry> players
    ) {
        public HostEntry {
            players = players == null ? List.of() : List.copyOf(players);
        }
    }

    public record PlayerEntry(String playerId, String playerName) {
    }
}
