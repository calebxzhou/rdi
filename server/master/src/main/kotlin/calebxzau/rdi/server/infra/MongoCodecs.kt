package calebxzau.rdi.server.infra

import calebxzhou.rdi.common.UUIDSerializer
import com.mongodb.MongoClientSettings
import java.util.UUID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistries.fromRegistries
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.kotlinx.defaultSerializersModule
import org.bson.codecs.pojo.PojoCodecProvider

/** Builds the codec registry used by the production Mongo client. */
@OptIn(ExperimentalSerializationApi::class)
fun productionMongoCodecRegistry(): CodecRegistry {
    val serializersModule = defaultSerializersModule + SerializersModule {
        contextual(UUID::class, UUIDSerializer)
    }
    return fromRegistries(
        fromProviders(KotlinSerializerCodecProvider(serializersModule)),
        MongoClientSettings.getDefaultCodecRegistry(),
        fromProviders(PojoCodecProvider.builder().automatic(true).build()),
    )
}
