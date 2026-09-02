package calebxzhou.rdi.client.ui.comp

import calebxzhou.rdi.common.model.AllGameRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameRuleModalTest {
    private val rules = AllGameRules.associateBy { it.id }

    @Test
    fun `known override value change is reported`() {
        val changes = findChangedGameRules(
            openingRules = mapOf("keepInventory" to "true"),
            draft = mapOf("keepInventory" to "false"),
            baseRuleById = rules,
        )

        assertEquals(
            listOf(GameRuleChange("keepInventory", "true", "false")),
            changes,
        )
    }

    @Test
    fun `known override restored to default is reported`() {
        val changes = findChangedGameRules(
            openingRules = mapOf("keepInventory" to "true"),
            draft = emptyMap(),
            baseRuleById = rules,
        )

        assertEquals(
            listOf(GameRuleChange("keepInventory", "true", "false")),
            changes,
        )
    }

    @Test
    fun `unchanged effective value is not reported`() {
        val changes = findChangedGameRules(
            openingRules = mapOf("keepInventory" to "true"),
            draft = mapOf("keepInventory" to "true"),
            baseRuleById = rules,
        )

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `unknown rule removal is reported`() {
        val changes = findChangedGameRules(
            openingRules = mapOf("customRule" to "custom-value"),
            draft = emptyMap(),
            baseRuleById = rules,
        )

        assertEquals(
            listOf(GameRuleChange("customRule", "custom-value", null)),
            changes,
        )
    }
}
