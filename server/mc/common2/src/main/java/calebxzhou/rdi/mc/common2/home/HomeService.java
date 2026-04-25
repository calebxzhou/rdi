package calebxzhou.rdi.mc.common2.home;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HomeService {
    private static final int MAX_HOME_NAME_LENGTH = 32;
    private static final int MAX_HOMES = 10;

    private HomeService() {
    }

    public static HomeResult setHome(HomePlayer player, String name) {
        var normalizedName = normalizeName(name);
        if (normalizedName == null) {
            return HomeResult.error("家名称不能为空");
        }
        if (normalizedName.length() > MAX_HOME_NAME_LENGTH) {
            return HomeResult.error("家名称最多" + MAX_HOME_NAME_LENGTH + "个字符");
        }
        if (normalizedName.contains("/") || normalizedName.contains("\\")) {
            return HomeResult.error("家名称不能包含/或\\");
        }

        var homes = new LinkedHashMap<>(player.loadHomes());
        if (!homes.containsKey(normalizedName) && homes.size() >= MAX_HOMES) {
            return HomeResult.error("最多只能设置" + MAX_HOMES + "个家");
        }
        homes.put(normalizedName, player.currentLocation());
        player.saveHomes(homes);
        return HomeResult.ok("已设置家" + normalizedName);
    }

    public static HomeResult goHome(HomePlayer player, String name) {
        var normalizedName = normalizeName(name);
        if (normalizedName == null) {
            return HomeResult.error("家名称不能为空");
        }

        var location = player.loadHomes().get(normalizedName);
        if (location == null) {
            return HomeResult.error("没有名为" + normalizedName + "的家");
        }

        var teleportResult = player.teleportTo(location);
        if (!teleportResult.success()) {
            return teleportResult;
        }
        return HomeResult.ok("已传送到家" + normalizedName);
    }

    public static HomeResult listHomes(HomePlayer player) {
        var homes = player.loadHomes();
        if (homes.isEmpty()) {
            return HomeResult.ok("还没有设置任何家");
        }
        var message = homes.entrySet().stream()
                .map(entry -> formatHome(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n"));
        return HomeResult.ok(message);
    }

    public static HomeResult deleteHome(HomePlayer player, String name) {
        var normalizedName = normalizeName(name);
        if (normalizedName == null) {
            return HomeResult.error("家名称不能为空");
        }

        var homes = new LinkedHashMap<>(player.loadHomes());
        if (homes.remove(normalizedName) == null) {
            return HomeResult.error("没有名为" + normalizedName + "的家");
        }
        player.saveHomes(homes);
        return HomeResult.ok("已删除家" + normalizedName);
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        var normalized = name.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String formatHome(String name, HomeLocation location) {
        return String.format(
                Locale.ROOT,
                "%s：%s x=%.1f y=%.1f z=%.1f",
                name,
                location.dimension(),
                location.x(),
                location.y(),
                location.z()
        );
    }
}
