package calebxzhou.rdi.mc.server.home;

import calebxzhou.rdi.mc.common2.home.HomeLocation;
import calebxzhou.rdi.mc.common2.home.HomePlayer;
import calebxzhou.rdi.mc.common2.home.HomeResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HomePlayer201 implements HomePlayer {
    private static final String RDI_TAG = "rdi";
    private static final String HOMES_TAG = "homes";
    private static final String DIMENSION_TAG = "dimension";
    private static final String X_TAG = "x";
    private static final String Y_TAG = "y";
    private static final String Z_TAG = "z";
    private static final String YAW_TAG = "yaw";
    private static final String PITCH_TAG = "pitch";

    private final ServerPlayer player;

    public HomePlayer201(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public HomeLocation currentLocation() {
        return new HomeLocation(
                player.serverLevel().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    @Override
    public Map<String, HomeLocation> loadHomes() {
        var result = new LinkedHashMap<String, HomeLocation>();
        var homesTag = homesTag();
        for (var homeName : homesTag.getAllKeys()) {
            var homeTag = homesTag.getCompound(homeName);
            result.put(homeName, new HomeLocation(
                    homeTag.getString(DIMENSION_TAG),
                    homeTag.getDouble(X_TAG),
                    homeTag.getDouble(Y_TAG),
                    homeTag.getDouble(Z_TAG),
                    homeTag.getFloat(YAW_TAG),
                    homeTag.getFloat(PITCH_TAG)
            ));
        }
        return result;
    }

    @Override
    public void saveHomes(Map<String, HomeLocation> homes) {
        var homesTag = new CompoundTag();
        homes.forEach((homeName, location) -> {
            var homeTag = new CompoundTag();
            homeTag.putString(DIMENSION_TAG, location.dimension());
            homeTag.putDouble(X_TAG, location.x());
            homeTag.putDouble(Y_TAG, location.y());
            homeTag.putDouble(Z_TAG, location.z());
            homeTag.putFloat(YAW_TAG, location.yaw());
            homeTag.putFloat(PITCH_TAG, location.pitch());
            homesTag.put(homeName, homeTag);
        });

        var persistedTag = persistedTag();
        var rdiTag = persistedTag.getCompound(RDI_TAG);
        rdiTag.put(HOMES_TAG, homesTag);
        persistedTag.put(RDI_TAG, rdiTag);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistedTag);
    }

    @Override
    public HomeResult teleportTo(HomeLocation location) {
        var dimensionLocation = ResourceLocation.tryParse(location.dimension());
        if (dimensionLocation == null) {
            return HomeResult.error("家的维度无效：" + location.dimension());
        }

        var dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        ServerLevel level = player.server.getLevel(dimensionKey);
        if (level == null) {
            return HomeResult.error("家的维度不存在：" + location.dimension());
        }

        player.teleportTo(level, location.x(), location.y(), location.z(), location.yaw(), location.pitch());
        return HomeResult.ok("OK");
    }

    private CompoundTag homesTag() {
        return persistedTag().getCompound(RDI_TAG).getCompound(HOMES_TAG);
    }

    private CompoundTag persistedTag() {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
}
