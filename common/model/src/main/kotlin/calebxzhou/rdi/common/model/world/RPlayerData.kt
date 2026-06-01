package calebxzhou.rdi.common.model.world

import net.benwoodworth.knbt.NbtCompound
import org.bson.types.ObjectId

data class RPlayerData(
    val _id: ObjectId = ObjectId(),
    val data: NbtCompound
)