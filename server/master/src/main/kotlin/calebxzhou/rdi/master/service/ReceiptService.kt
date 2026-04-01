package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Receipt
import calebxzhou.rdi.master.DB
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.idParam
import calebxzhou.rdi.master.net.response
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.firstOrNull
import org.bson.types.ObjectId

fun Route.receiptRoutes() = route("/receipt") {
    get("/{receiptId}") {
        response(data = ReceiptService.getMsg(idParam("receiptId")))
    }
}

object ReceiptService {
    private const val REQUESTER_MAX_LEN = 120
    private const val MSG_MAX_LEN = 4000

    val receiptCol = DB.getCollection<Receipt>("receipt")

    suspend fun getById(receiptId: ObjectId): Receipt? =
        receiptCol.find(eq("_id", receiptId))
            .limit(1)
            .firstOrNull()

    suspend fun getMsg(receiptId: ObjectId): String =
        getById(receiptId)?.msg ?: throw RequestError("无此回执")

    suspend fun insertReceipt(id: ObjectId,requester: String, msg: String = ""): Receipt {
        val receipt = Receipt(
            id,
            requester = normalizeRequester(requester),
            msg = normalizeMsg(msg)
        )
        receiptCol.deleteOne(eq("_id",id))
        receiptCol.insertOne(receipt)
        return receipt
    }

    suspend fun changeMsg(receiptId: ObjectId, msg: String): Receipt {
        val normalizedMsg = normalizeMsg(msg)
        val updateResult = receiptCol.updateOne(
            eq("_id", receiptId),
            Updates.set(Receipt::msg.name, normalizedMsg)
        )
        if (updateResult.matchedCount == 0L) {
            throw RequestError("无此回执")
        }
        return getById(receiptId) ?: throw RequestError("无此回执")
    }

    private fun normalizeRequester(requester: String): String {
        val normalized = requester.trim()
        if (normalized.isEmpty()) throw ParamError("requester不能为空")
        if (normalized.length > REQUESTER_MAX_LEN) throw ParamError("requester过长")
        return normalized
    }

    private fun normalizeMsg(msg: String): String {
        val normalized = msg.trim()
        if (normalized.length > MSG_MAX_LEN) throw ParamError("msg过长")
        return normalized
    }
}
