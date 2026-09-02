package calebxzhou.rdi.master.model

import calebxzhou.rdi.common.model.HwSpec
import org.bson.types.ObjectId


data class AuthLog(
    val _id: ObjectId = ObjectId(),
    val uid: ObjectId,
    //登录true登出false
    val login: Boolean,
    val ip: String,
    //只有登入
    val spec: HwSpec?,

) {
}