package calebxzhou.rdi.mc.common2.mcp

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.util.*

/*@OptIn(ExperimentalSerializationApi::class)
val csv = Csv {
    hasHeaderRecord = true
    ignoreEmptyLines = true
}*/
val json = Json {
    serializersModule = SerializersModule {
        contextual(UUID::class, UUIDSerializer)
    }
    ignoreUnknownKeys = true
    isLenient = true // Allows parsing of malformed JSON
    coerceInputValues = true // Helps with default values and nulls
}
val yml = Yaml.default
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())

    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())

    }
}
