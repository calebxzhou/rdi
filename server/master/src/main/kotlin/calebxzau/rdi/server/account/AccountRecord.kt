package calebxzau.rdi.server.account

import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.toUUID
import java.util.UUID

//PgSQL only
data class AccountRecord(
    val id: UUID,
    val name: String,
    val pwd: String,
    val qq: String,
    val msid: UUID?,
    val isSlim: Boolean,
    val skin: String,
    val cape: String?
) {
    companion object {
        fun from(account: RAccount): AccountRecord {
            val cloth = account.cloth
            return AccountRecord(
                id = account._id.toUUID(),
                name = account.name,
                pwd = account.pwd,
                qq = account.qq,
                msid = account.msid,
                isSlim = cloth.isSlim,
                skin = cloth.skin,
                cape = cloth.cape?.takeUnless { it == "null" }
            )
        }
    }
}
