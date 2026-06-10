package calebxzhou.rdi.master.service

import calebxzhou.mykotutils.hwspec.HwSpec
import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.getDateTimeNow
import calebxzhou.mykotutils.std.isValidHttpUrl
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.MojangPlayerProfile
import calebxzhou.rdi.common.model.MsaAccountInfo
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.MojangApi
import calebxzhou.rdi.common.service.MojangApi.dashless
import calebxzhou.rdi.common.util.ok
import calebxzhou.rdi.common.util.validatePlayerName
import calebxzhou.rdi.master.CRASH_REPORT_DIR
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.exception.AuthError
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.model.AuthLog
import calebxzhou.rdi.master.net.*
import calebxzhou.rdi.master.service.PlayerService.bindMSAccount
import calebxzhou.rdi.master.service.PlayerService.changeCloth
import calebxzhou.rdi.master.service.PlayerService.changeProfile
import calebxzhou.rdi.master.service.PlayerService.clearCloth
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Updates
import com.mongodb.client.model.Updates.combine
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.io.File

fun Route.playerRoutes() {
    route("/player") {
        route("/{uid}") {
            get("/name") {
                val uid = ObjectId(param("uid"))
                val name = PlayerService.getName(uid) ?: "【玩家不存在】"
                response(data = name)
            }
            get("/skin") {
                val uidParam = param("uid")
                val uid = ObjectId(uidParam)
                val cloth = PlayerService.getSkin(uid)
                response(data = cloth)
            }
            get("/info") {
                val uid = ObjectId(param("uid"))
                val info = PlayerService.getInfo(uid)
                response(data = info)
            }
        }
        get("/infos") {
            paramNull("ids")?.split("\n")?.map { ObjectId(it) }?.let { ids ->
                val infos = PlayerService.getInfoByIds(ids)
                response(data = infos)
                return@get
            }
            paramNull("names")?.split("\n")?.let { names ->
                val infos = PlayerService.getInfoByNames(names)
                response(data = infos)
                return@get
            }
            response(data = emptyList<RAccount.Dto>())
        }
        post("/register") {
            PlayerService.registerMsa(call.receive())
            ok()
        }
        post("/reset-pwd/msa") {
            PlayerService.resetPasswordByMsa(call.receive())
            ok()
        }
        post("/jwt") {
            PlayerService.validate(
                param("usr"),
                param("pwd")
            )?.let { JwtService.generateToken(it._id) }
                ?.let { response(data = it) }
                ?: err("账密×")
        }
        post("/login") {
            val result = PlayerService.login(
                param("usr"),
                param("pwd"),
                paramNull("spec"),
                call.clientIp
            )
            call.response.header("jwt", JwtService.generateToken(result._id))
            response(data = result)
        }
        post("/crash-report") {
            val uid = ObjectId(param("uid"))
            val report = param("report")
            PlayerService.saveCrashReport(uid, report)
            ok()
        }

        authenticate("auth-jwt", optional = true) {
            route("/skin") {
                post {
                    call.player()
                        .changeCloth(
                            paramNull("isSlim")?.toBoolean() ?: false,
                            param("skin"),
                            paramNull("cape")
                        )
                    ok()
                }
                delete {
                    call.player()
                        .clearCloth()
                    ok()
                }
            }
            put("/profile") {
                call.player()
                    .changeProfile(paramNull("name"), paramNull("qq"), paramNull("pwd"))
                ok()
            }
            post("/bind-ms") {
                call.player().bindMSAccount(call.receive())
                ok()
            }
        }
    }
}

suspend fun ApplicationCall.player(): RAccount = PlayerService.getById(uid) ?: throw RequestError("用户不存在")

object PlayerService {
    val accountCol = DB.getCollection<RAccount>("account")
    val authLogCol = DB.getCollection<AuthLog>("auth_log")
    private val lgr by Loggers

    data class LoginResult(val account: RAccount, val token: String)

    suspend fun getByQQ(qq: String): RAccount? = accountCol.find(eq("qq", qq)).firstOrNull()
    suspend fun getByName(name: String): RAccount? = accountCol.find(eq("name", name)).firstOrNull()
    suspend fun getByMsid(msid: java.util.UUID): RAccount? = accountCol.find(eq("msid", msid)).firstOrNull()

    suspend fun get(usr: String): RAccount? {
        if (ObjectId.isValid(usr)) {
            return getById(ObjectId(usr))
        }
        return getByQQ(usr)
            ?: getByName(usr)
    }

    val RAccount.uidFilter
        get() = equalById(_id)

    fun equalById(id: ObjectId): Bson = eq("_id", id)
    fun equalById(acc: RAccount): Bson = eq("_id", acc._id)


    suspend fun getById(id: ObjectId): RAccount? = accountCol.find(equalById(id)).firstOrNull()

    suspend fun has(id: ObjectId): Boolean = accountCol
        .countDocuments(equalById(id)) > 0

    suspend fun hasQQ(qq: String): Boolean = accountCol
        .countDocuments(eq("qq", qq)) > 0

    suspend fun hasName(name: String): Boolean = accountCol
        .countDocuments(eq("name", name)) > 0

    suspend fun validate(usr: String, pwd: String): RAccount? {
        val account = get(usr)
        return if (account == null || account.pwd != pwd) null else account
    }
    suspend fun RAccount.clearCloth() {
        accountCol.updateOne(uidFilter, Updates.unset(RAccount::cloth.name))
    }

    suspend fun RAccount.changeCloth(isSlim: Boolean, skin: String, cape: String?) {
        if (!skin.isValidHttpUrl()) throw ParamError("皮肤链接格式错误")
        if (cape != null && !cape.isValidHttpUrl()) throw ParamError("披风链接格式错误")
        accountCol.updateOne(uidFilter, Updates.set(RAccount::cloth.name, RAccount.Cloth(isSlim, skin, cape)))
    }

    suspend fun getSkin(uid: ObjectId): RAccount.Cloth {
        val cloth = getById(uid)?.cloth ?: RAccount.Cloth()
        val normalizedCape = cloth.cape?.takeUnless { it == "null" }
        return cloth.copy(cape = normalizedCape)
    }

    suspend fun RAccount.RegisterDto.validate(): Result<Unit> {
        if (hasQQ(qq)) throw RequestError("QQ被占用")
        if (hasName(name)) throw RequestError("昵称被占用")
        name.validatePlayerName().getOrThrow()
        if (qq.length !in 5..10 || !qq.all { it.isDigit() }) {
            throw RequestError("QQ号格式不正确")
        }
        validatePassword(pwd)
        return ok()
    }

    suspend fun MsaAccountInfo.validate(): Result<MojangPlayerProfile> {
        if (getByMsid(uuid) != null) {
            throw RequestError("此微软账号已被使用")
        }
        return verifyMsaProfile()
    }

    suspend fun MsaAccountInfo.verifyMsaProfile(): Result<MojangPlayerProfile> {
        val msprof = MojangApi.getProfileByToken(token).getOrElse {
            it.printStackTrace()
            throw RequestError("无效的MC会话，请重新登录")
        }.apply {
            if (id != uuid.dashless) {
                throw RequestError("登录会话不匹配 请重试")
            }
        }
        return ok(msprof)
    }

    private fun validatePassword(pwd: String) {
        if (pwd.length !in 6..16) {
            throw RequestError("密码长度须在6~16个字符")
        }
    }

    suspend fun registerMsa(dto: RAccount.RegisterDto) {
        if (dto.msa==null) {
            throw RequestError("没有微软账号信息")
        }
        addAccount(dto)
    }
    suspend fun addAccount(dto: RAccount.RegisterDto): Result<RAccount>{
        dto.validate()
        val cloth = dto.msa?.validate()?.getOrElse {
            throw RequestError("微软账号认证错误：${it.message}")
        }?.extractMSACloth()?: RAccount.Cloth()
        val account = RAccount(
            _id = ObjectId(),
            name = dto.name,
            pwd = dto.pwd,
            qq = dto.qq,
            msid = dto.msa?.uuid,
            cloth = cloth
        )
        accountCol.insertOne(account)
        return ok(account)
    }
    private fun MojangPlayerProfile.extractMSACloth(): RAccount.Cloth {
        val msprof = this
        // Get skin: prefer ACTIVE, then first, then default
        val activeSkin = msprof.skins.firstOrNull { it.state == "ACTIVE" }
        val selectedSkin = activeSkin ?: msprof.skins.firstOrNull()

        // Get cape: prefer ACTIVE, then first, then null
        val activeCape = msprof.capes.firstOrNull { it.state == "ACTIVE" }
        val selectedCape = activeCape ?: msprof.capes.firstOrNull()

        val cloth = RAccount.Cloth(
            isSlim = selectedSkin?.variant == "SLIM",
            skin = selectedSkin?.url ?: RAccount.Cloth().skin,
            cape = selectedCape?.url
        )
        return cloth
    }

    suspend fun RAccount.bindMSAccount(msa: MsaAccountInfo) {
        val msprof = msa.validate().getOrElse { throw RequestError("微软账号认证错误：${it.message}") }
        val cloth = msprof.extractMSACloth()
        // Update cloth and msid in database
        accountCol.updateOne(
            uidFilter,
            combine(
                Updates.set(RAccount::cloth.name, cloth),
                Updates.set("msid", msa.uuid)
            )
        )
    }

    suspend fun resetPasswordByMsa(dto: RAccount.ResetPasswordByMsaDto) {
        validatePassword(dto.newPwd)
        dto.msa.verifyMsaProfile().getOrElse {
            throw RequestError("微软账号认证错误：${it.message}")
        }
        val account = getByMsid(dto.msa.uuid)
            ?: throw RequestError("未找到绑定此微软账号的账号")
        accountCol.updateOne(account.uidFilter, Updates.set(RAccount::pwd.name, dto.newPwd))
    }

    suspend fun resetPasswordByQqMail(dto: RAccount.ResetPasswordByQqMailDto, senderQq: String) {
        validatePassword(dto.newPwd)
        if (dto.qq.length !in 5..10 || !dto.qq.all { it.isDigit() }) {
            throw RequestError("QQ号格式不正确")
        }
        if (senderQq != dto.qq) {
            throw RequestError("resetPwd邮件发件人QQ与目标QQ不一致")
        }
        val account = getByQQ(dto.qq) ?: throw RequestError("无此账号")
        accountCol.updateOne(account.uidFilter, Updates.set(RAccount::pwd.name, dto.newPwd))
    }

    suspend fun login(usr: String, pwd: String, specJson: String?, clientIp: String): RAccount {
        val account = validate(usr, pwd) ?: run {
            lgr.info { "${usr}登录失败" }
            throw AuthError("密码错误")
        }
        lgr.info { "${account.name} ${account.qq}登录成功" }
        specJson?.let { json ->
            val spec = try {
                serdesJson.decodeFromString<HwSpec>(json)
            } catch (ex: Exception) {
                throw ParamError("硬件信息格式错误")
            }.let { spec ->
                if (spec.videoMode.size > 10) spec.copy(videoMode = spec.videoMode.take(10)) else spec
            }
            authLogCol.insertOne(
                AuthLog(
                    uid = account._id,
                    login = true,
                    ip = clientIp,
                    spec = spec
                )
            )
        }
        return account
    }

    suspend fun RAccount.changeProfile(newName: String?, newQq: String?, newPwd: String?) {
        val updates = mutableListOf<Bson>()
        newQq?.let {
            if (getByQQ(it) != null) throw RequestError("QQ用过了")
            updates += Updates.set("qq", it)
        }
        newName?.let {
            if (getByName(it) != null) throw RequestError("名字用过了")
            updates += Updates.set("name", it)
        }
        newPwd?.let {
            validatePassword(it)
            updates += Updates.set("pwd", it)
        }
        if (updates.isNotEmpty()) {
            accountCol.updateOne(uidFilter, combine(updates))
        }
    }

    suspend fun getName(uid: ObjectId): String? = getById(uid)?.name
    suspend fun List<ObjectId>.getPlayerNames(): Map<ObjectId, String> {
        if (isEmpty()) return emptyMap()
        val uniqueIds = LinkedHashSet(this)
        val names = accountCol.find(`in`("_id", uniqueIds.toList()))
            .toList()
            .associate { account -> account._id to account.name }
            .toMutableMap()

        uniqueIds.forEach { id ->
            names.putIfAbsent(id, "未知")
        }

        return names
    }

    suspend fun getInfo(uid: ObjectId): RAccount.Dto = getById(uid)?.dto ?: RAccount.DEFAULT.dto

    suspend fun getInfoByNames(names: List<String>): List<RAccount.Dto> =
        names.map { getByName(it)?.dto ?: RAccount.DEFAULT.dto }

    suspend fun getInfoByIds(uids: List<ObjectId>): List<RAccount.Dto> =
        uids.map { getById(it)?.dto ?: RAccount.DEFAULT.dto }

    suspend fun saveCrashReport(uid: ObjectId, report: String) {
        val account = getById(uid)
        val fileName = "${account?.name ?: "未知"}-${account?.qq ?: "0"}-${getDateTimeNow("yyyyMMdd-HHmmss")}.txt"
        File(CRASH_REPORT_DIR, fileName).writeText(report)
    }
}
