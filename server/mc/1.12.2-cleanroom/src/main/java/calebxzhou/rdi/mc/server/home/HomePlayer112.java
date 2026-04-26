package calebxzhou.rdi.mc.server.home;

import calebxzhou.rdi.mc.common2.home.HomeLocation;
import calebxzhou.rdi.mc.common2.home.HomePlayer;
import calebxzhou.rdi.mc.common2.home.HomeResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HomePlayer112 implements HomePlayer {
    private static final String RDI_TAG = "rdi";
    private static final String HOMES_TAG = "homes";
    private static final String DIMENSION_TAG = "dimension";
    private static final String X_TAG = "x";
    private static final String Y_TAG = "y";
    private static final String Z_TAG = "z";
    private static final String YAW_TAG = "yaw";
    private static final String PITCH_TAG = "pitch";

    private final EntityPlayerMP player;

    public HomePlayer112(EntityPlayerMP player) {
        this.player = player;
    }

    @Override
    public HomeLocation currentLocation() {
        return new HomeLocation(
                Integer.toString(player.dimension),
                player.posX,
                player.posY,
                player.posZ,
                player.rotationYaw,
                player.rotationPitch
        );
    }

    @Override
    public Map<String, HomeLocation> loadHomes() {
        var result = new LinkedHashMap<String, HomeLocation>();
        var homesTag = homesTag();
        for (var homeName : homesTag.getKeySet()) {
            var homeTag = homesTag.getCompoundTag(homeName);
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
        var homesTag = new NBTTagCompound();
        homes.forEach((homeName, location) -> {
            var homeTag = new NBTTagCompound();
            homeTag.setString(DIMENSION_TAG, location.dimension());
            homeTag.setDouble(X_TAG, location.x());
            homeTag.setDouble(Y_TAG, location.y());
            homeTag.setDouble(Z_TAG, location.z());
            homeTag.setFloat(YAW_TAG, location.yaw());
            homeTag.setFloat(PITCH_TAG, location.pitch());
            homesTag.setTag(homeName, homeTag);
        });

        var persistedTag = persistedTag();
        var rdiTag = persistedTag.getCompoundTag(RDI_TAG);
        rdiTag.setTag(HOMES_TAG, homesTag);
        persistedTag.setTag(RDI_TAG, rdiTag);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistedTag);
    }

    @Override
    public HomeResult teleportTo(HomeLocation location) {
        int dimension;
        try {
            dimension = Integer.parseInt(location.dimension());
        } catch (NumberFormatException e) {
            return HomeResult.error("家的维度无效：" + location.dimension());
        }

        WorldServer world = player.server.getWorld(dimension);
        if (world == null) {
            return HomeResult.error("家的维度不存在：" + location.dimension());
        }

        if (player.dimension != dimension) {
            player.server.getPlayerList().transferPlayerToDimension(player, dimension, (targetWorld, entity, yaw) ->
                    entity.setLocationAndAngles(location.x(), location.y(), location.z(), location.yaw(), location.pitch())
            );
        }
        player.connection.setPlayerLocation(location.x(), location.y(), location.z(), location.yaw(), location.pitch());
        return HomeResult.ok("OK");
    }

    private NBTTagCompound homesTag() {
        return persistedTag().getCompoundTag(RDI_TAG).getCompoundTag(HOMES_TAG);
    }

    private NBTTagCompound persistedTag() {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
}
