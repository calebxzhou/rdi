package calebxzau.rdi.server.service.baseworld

import calebxzau.rdi.common.model.BaseWorld
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Synchronous Exposed operations; callers own the surrounding transaction. */
class PgBaseWorldRepo {
    fun create(
        ownerId: UUID,
        name: String,
        levelType: String,
        generatorSettings: String?,
        size: Long,
    ): BaseWorld =
        BaseWorldTable.insertReturning {
            it[BaseWorldTable.ownerId] = ownerId
            it[BaseWorldTable.name] = name
            it[BaseWorldTable.levelType] = levelType
            it[BaseWorldTable.generatorSettings] = generatorSettings
            it[BaseWorldTable.size] = size
        }.single().toBaseWorld()

    fun findById(ownerId: UUID, id: UUID): BaseWorld? =
        BaseWorldTable.selectAll()
            .where { (BaseWorldTable.ownerId eq ownerId) and (BaseWorldTable.id eq id) }
            .singleOrNull()
            ?.toBaseWorld()

    fun findById(id: UUID): BaseWorld? =
        BaseWorldTable.selectAll()
            .where { BaseWorldTable.id eq id }
            .singleOrNull()
            ?.toBaseWorld()

    fun listByOwner(ownerId: UUID): List<BaseWorld> =
        BaseWorldTable.selectAll()
            .where { BaseWorldTable.ownerId eq ownerId }
            .orderBy(BaseWorldTable.id, SortOrder.DESC)
            .map { it.toBaseWorld() }

    fun listAll(): List<BaseWorld> =
        BaseWorldTable.selectAll()
            .orderBy(BaseWorldTable.id, SortOrder.DESC)
            .map { it.toBaseWorld() }

    fun countByOwner(ownerId: UUID): Long =
        BaseWorldTable.selectAll()
            .where { BaseWorldTable.ownerId eq ownerId }
            .count()

    fun delete(ownerId: UUID, id: UUID): Boolean =
        BaseWorldTable.deleteWhere {
            (BaseWorldTable.ownerId eq ownerId) and (BaseWorldTable.id eq id)
        } == 1

    fun updateSize(ownerId: UUID, id: UUID, size: Long): BaseWorld? {
        BaseWorldTable.update({ (BaseWorldTable.ownerId eq ownerId) and (BaseWorldTable.id eq id) }) {
            it[BaseWorldTable.size] = size
        }
        return findById(ownerId, id)
    }
}

private object BaseWorldTable : Table("base_world") {
    val id = javaUUID("id").databaseGenerated()
    val ownerId = javaUUID("owner_id")
    val name = text("name")
    val levelType = text("level_type")
    val generatorSettings = text("generator_settings").nullable()
    val size = long("size")

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toBaseWorld(): BaseWorld = BaseWorld(
    id = this[BaseWorldTable.id],
    ownerId = this[BaseWorldTable.ownerId],
    name = this[BaseWorldTable.name],
    levelType = this[BaseWorldTable.levelType],
    generatorSettings = this[BaseWorldTable.generatorSettings],
    size = this[BaseWorldTable.size]
)
