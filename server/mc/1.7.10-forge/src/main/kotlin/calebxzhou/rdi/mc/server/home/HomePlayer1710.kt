package calebxzhou.rdi.mc.server.home

import calebxzhou.rdi.mc.rcmd.home.HomeLocation
import calebxzhou.rdi.mc.rcmd.home.HomePlayer
import calebxzhou.rdi.mc.rcmd.home.HomeResult
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.dedicated.DedicatedServer

class HomePlayer1710(private val server: DedicatedServer, private val player: EntityPlayerMP) : HomePlayer {
    override fun currentLocation(): HomeLocation {
        return HomeLocation(
            dimensionName(player.dimension),
            player.posX,
            player.posY,
            player.posZ,
            player.rotationYaw,
            player.rotationPitch
        )
    }

    override fun loadHomes(): MutableMap<String, HomeLocation> {
        val result = LinkedHashMap<String, HomeLocation>()
        val homesTag = homesTag()
        for (homeName in homesTag.func_150296_c()) {
            val homeTag = homesTag.getCompoundTag(homeName)
            result.put(
                homeName, HomeLocation(
                    homeTag.getString(DIMENSION_TAG),
                    homeTag.getDouble(X_TAG),
                    homeTag.getDouble(Y_TAG),
                    homeTag.getDouble(Z_TAG),
                    homeTag.getFloat(YAW_TAG),
                    homeTag.getFloat(PITCH_TAG)
                )
            )
        }
        return result
    }

    override fun saveHomes(homes: MutableMap<String, HomeLocation>) {
        val homesTag = NBTTagCompound()
        homes.forEach { (homeName: String, location: HomeLocation) ->
            val homeTag = NBTTagCompound()
            homeTag.setString(DIMENSION_TAG, location!!.dimension)
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
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistedTag)
    }

    override fun teleportTo(location: HomeLocation): HomeResult {
        val dimensionId = parseDimension(location.dimension)
        if (dimensionId == null) {
            return HomeResult.error("家的维度无效：" + location.dimension)
        }

        val targetWorld = server.worldServerForDimension(dimensionId)
        if (targetWorld == null) {
            return HomeResult.error("家的维度不存在：" + location.dimension)
        }

        if (player.dimension != dimensionId) {
            server.getConfigurationManager().transferPlayerToDimension(player, dimensionId)
        }
        player.fallDistance = 0.0f
        player.playerNetServerHandler.setPlayerLocation(
            location.x,
            location.y,
            location.z,
            location.yaw,
            location.pitch
        )
        return HomeResult.ok("OK")
    }

    private fun homesTag(): NBTTagCompound {
        return persistedTag().getCompoundTag(RDI_TAG).getCompoundTag(HOMES_TAG)
    }

    private fun persistedTag(): NBTTagCompound {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
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

        private fun dimensionName(dimensionId: Int): String {
            return "legacy:" + dimensionId
        }

        private fun parseDimension(dimension: String): Int? {
            try {
                if (dimension.startsWith("legacy:")) {
                    return dimension.substring("legacy:".length).toInt()
                }
                return dimension.toInt()
            } catch (e: NumberFormatException) {
                return null
            }
        }
    }
}
