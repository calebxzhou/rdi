package calebxzhou.rdi.mc.client.mcpimpl

import calebxzhou.rdi.mc.client.compat.RJeiRuntimeStore
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess

object RecipeProcessIndex {
    @Volatile
    private var snapshot = Snapshot.EMPTY

    fun recipesByOutputItem(itemId: String): List<RecipeProcess> {
        return currentSnapshot().byOutputItem[itemId].orEmpty()
    }

    fun clear() {
        snapshot = Snapshot.EMPTY
    }

    fun isReady(): Boolean {
        return RJeiRuntimeStore.runtime != null
    }

    private fun currentSnapshot(): Snapshot {
        val runtime = RJeiRuntimeStore.runtime ?: return Snapshot.EMPTY.also { snapshot = it }
        val key = RecipeIndexKey(RJeiRuntimeStore.generation)
        snapshot.takeIf { it.key == key }?.let { return it }
        val processes = JeiRecipeProcessCollector.collect(runtime)
        return Snapshot(
            key = key,
            processes = processes,
            byOutputItem = processes
                .flatMap { process -> process.outputs.map { it.itemId to process } }
                .groupBy({ it.first }, { it.second }),
        ).also { snapshot = it }
    }

    private data class RecipeIndexKey(val jeiGeneration: Int)

    private data class Snapshot(
        val key: RecipeIndexKey? = null,
        val processes: List<RecipeProcess> = emptyList(),
        val byOutputItem: Map<String, List<RecipeProcess>> = emptyMap(),
    ) {
        companion object {
            val EMPTY = Snapshot()
        }
    }
}
