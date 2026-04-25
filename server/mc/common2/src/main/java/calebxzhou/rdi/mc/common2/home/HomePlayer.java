package calebxzhou.rdi.mc.common2.home;

import java.util.Map;

public interface HomePlayer {
    HomeLocation currentLocation();

    Map<String, HomeLocation> loadHomes();

    void saveHomes(Map<String, HomeLocation> homes);

    HomeResult teleportTo(HomeLocation location);
}
