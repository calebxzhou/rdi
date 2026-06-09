package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.home.HomeLocation
import calebxzhou.rdi.mc.rcmd.home.HomePlayer
import calebxzhou.rdi.mc.rcmd.home.HomeResult
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.LinkedHashMap

class HomePlayer211(player: ServerPlayer) : HomePlayer {
    private val player: ServerPlayer

    init {
        this.player = player
    }

    public override fun currentLocation(): HomeLocation {
        return HomeLocation(
            player.serverLevel().dimension().location().toString(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot()
        )
    }

    public override fun loadHomes(): MutableMap<String, HomeLocation> {
        val result: LinkedHashMap<String, HomeLocation> =
            LinkedHashMap<String, HomeLocation>()
        val homesTag: CompoundTag = homesTag()
        for (homeName in homesTag.getAllKeys()) {
            val homeTag: CompoundTag = homesTag.getCompound(homeName)
            result[homeName] = HomeLocation(
                homeTag.getString(DIMENSION_TAG),
                homeTag.getDouble(X_TAG),
                homeTag.getDouble(Y_TAG),
                homeTag.getDouble(Z_TAG),
                homeTag.getFloat(YAW_TAG),
                homeTag.getFloat(PITCH_TAG)
            )
        }
        return result
    }

    public override fun saveHomes(homes: MutableMap<String, HomeLocation>) {
        val homesTag: CompoundTag = CompoundTag()
        homes.forEach { (homeName: String, location: HomeLocation) ->
            val homeTag: CompoundTag = CompoundTag()
            homeTag.putString(DIMENSION_TAG, location.dimension)
            homeTag.putDouble(X_TAG, location.x)
            homeTag.putDouble(Y_TAG, location.y)
            homeTag.putDouble(Z_TAG, location.z)
            homeTag.putFloat(YAW_TAG, location.yaw)
            homeTag.putFloat(PITCH_TAG, location.pitch)
            homesTag.put(homeName, homeTag)
        }

        val persistedTag: CompoundTag = persistedTag()
        val rdiTag: CompoundTag = persistedTag.getCompound(RDI_TAG)
        rdiTag.put(HOMES_TAG, homesTag)
        persistedTag.put(RDI_TAG, rdiTag)
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistedTag)
    }

    public override fun teleportTo(location: HomeLocation): HomeResult {
        val dimensionLocation = ResourceLocation.tryParse(location.dimension)
        if (dimensionLocation == null) {
            return HomeResult.error("家的维度无效：" + location.dimension)
        }

        val dimensionKey: ResourceKey<Level> =
            ResourceKey.create<Level>(
                Registries.DIMENSION,
                dimensionLocation
            )
        val level =
            player.server.getLevel(dimensionKey) ?: return HomeResult.error("家的维度不存在：" + location.dimension)

        player.teleportTo(level, location.x, location.y, location.z, location.yaw, location.pitch)
        return HomeResult.ok("OK")
    }

    private fun homesTag(): CompoundTag {
        return persistedTag().getCompound(RDI_TAG)
            .getCompound(HOMES_TAG)
    }

    private fun persistedTag(): CompoundTag {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
    }

    companion object {
        private const val RDI_TAG = "rdi"
        private const val HOMES_TAG = "homes"
        private const val DIMENSION_TAG = "dimension"
        private const val X_TAG = "x"
        private const val Y_TAG = "y"
        private const val Z_TAG = "z"
        private const val YAW_TAG = "yaw"
        private const val PITCH_TAG = "pitch"
    }
}