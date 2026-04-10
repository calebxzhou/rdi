package calebxzhou.rdi.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bson.BsonBinary
import org.bson.UuidRepresentation
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import org.bson.types.ObjectId
import java.util.UUID

/**
 * calebxzhou @ 2024-06-18 14:25
 */
val module = SerializersModule {
    contextual(ObjectId::class, ObjectIdSerializer)
    contextual(UUID::class, UUIDSerializer)
}

// Use the module with JSON configuration
val serdesJson = Json {
    serializersModule = module
    ignoreUnknownKeys = true
    isLenient = true // Allows parsing of malformed JSON
    coerceInputValues = true // Helps with default values and nulls
}


inline val <reified T> T.json: String
    get() = serdesJson.encodeToString<T>(this)

inline fun <reified T> String.deser(): Result<T> = runCatching { serdesJson.decodeFromString<T>(this) }

object ObjectIdSerializer : KSerializer<ObjectId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ObjectId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ObjectId) {
        encoder.encodeString(value.toHexString())
    }

    override fun deserialize(decoder: Decoder): ObjectId {
        return ObjectId(decoder.decodeString())
    }
}

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: UUID) {
        when (encoder) {
            is BsonEncoder -> {
                // For MongoDB BSON, encode as Binary UUID
                encoder.encodeBsonValue(BsonBinary(value, UuidRepresentation.STANDARD))
            }
            else -> {
                // For JSON and other formats, encode as String
                encoder.encodeString(value.toString())
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): UUID {
        return when (decoder) {
            is BsonDecoder -> {
                // For MongoDB BSON, decode from Binary
                val bsonBinary = decoder.decodeBsonValue().asBinary()
                bsonBinary.asUuid(UuidRepresentation.STANDARD)
            }
            else -> {
                // For JSON and other formats, decode from String
                UUID.fromString(decoder.decodeString())
            }
        }
    }
}
