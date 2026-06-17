package calebxzhou.rdi.mc.server.home

import calebxzhou.rdi.mc.rcmd.home.HomeLocation
import calebxzhou.rdi.mc.rcmd.home.HomePlayer
import calebxzhou.rdi.mc.rcmd.home.HomeResult
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.util.ITeleporter

class HomePlayer112(private val player: EntityPlayerMP) : HomePlayer {
    override fun currentLocation(): HomeLocation =
        HomeLocation(
            player.dimension.toString(),
            player.posX,
            player.posY,
            player.posZ,
            player.rotationYaw,
            player.rotationPitch
        )

    override fun loadHomes(): MutableMap<String, HomeLocation> {
        val result = linkedMapOf<String, HomeLocation>()
        val homesTag = homesTag()
        for (homeName in homesTag.keySet) {
            val homeTag = homesTag.getCompoundTag(homeName)
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

    override fun saveHomes(homes: MutableMap<String, HomeLocation>) {
        val homesTag = NBTTagCompound()
        homes.forEach { (homeName, location) ->
            val homeTag = NBTTagCompound()
            homeTag.setString(DIMENSION_TAG, location.dimension)
            homeTag.setDouble(X_TAG, location.x)
            homeTag.setDouble(Y_TAG, location.y)
            homeTag.setDouble(Z_TAG, location.z)
            homeTag.setFloat(YAW_TAG, location.yaw)
            homeTag.setFloat(PITCH_TAG, location.pitch)
            homesTag.setTag(homeName, homeTag)
        }

        val persistedTag = persistedTag()
        val rdiTag = persistedTag.getCompoundTag(RDI_TAG)
        rdiTag.setTag(HOMES_TAG, homesTag)
        persistedTag.setTag(RDI_TAG, rdiTag)
        player.entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistedTag)
    }

    override fun teleportTo(location: HomeLocation): HomeResult {
        val dimension = location.dimension.toIntOrNull()
            ?: return HomeResult.error("家的维度无效：${location.dimension}")

        val world = player.server.getWorld(dimension)
        if (world == null) {
            return HomeResult.error("家的维度不存在：${location.dimension}")
        }

        if (player.dimension != dimension) {
            player.server.playerList.transferPlayerToDimension(
                player,
                dimension,
                ITeleporter { _: World?, entity: Entity?, _: Float ->
                    entity?.setLocationAndAngles(
                        location.x,
                        location.y,
                        location.z,
                        location.yaw,
                        location.pitch
                    )
                }
            )
        }
        player.connection.setPlayerLocation(location.x, location.y, location.z, location.yaw, location.pitch)
        return HomeResult.ok("OK")
    }

    private fun homesTag(): NBTTagCompound =
        persistedTag().getCompoundTag(RDI_TAG).getCompoundTag(HOMES_TAG)

    private fun persistedTag(): NBTTagCompound =
        player.entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)

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
