package calebxzau.rdi.client.codeeditor

import calebxzau.rdi.client.codeeditor.CodeLanguage
import calebxzau.rdi.client.codeeditor.CodeTokenRole
import calebxzau.rdi.client.codeeditor.highlightCode
import kotlin.test.Test
import kotlin.test.assertTrue

class SyntaxHighlighterTest {
    @Test
    fun preservesTextAndHighlightsJsonTokens() {
        val text = "{\"name\": 1, \"enabled\": true}"
        val spans = highlightCode(text, CodeLanguage.JSON)

        assertTrue(spans.all { it.start >= 0 && it.start < it.endExclusive && it.endExclusive <= text.length })
        assertTrue(spans.any { it.role == CodeTokenRole.JSON_KEY && text.substring(it.start, it.endExclusive) == "\"name\"" })
        assertTrue(spans.any { it.role == CodeTokenRole.JSON_NUMBER && text.substring(it.start, it.endExclusive) == "1" })
        assertTrue(spans.any { it.role == CodeTokenRole.JSON_LITERAL && text.substring(it.start, it.endExclusive) == "true" })
    }

    @Test
    fun highlightsYamlCommentsAndKeys() {
        val text = "name: value # comment"
        val spans = highlightCode(text, CodeLanguage.YAML)

        assertTrue(spans.any { it.role == CodeTokenRole.YAML_KEY && text.substring(it.start, it.endExclusive) == "name" })
        assertTrue(spans.any { it.role == CodeTokenRole.YAML_COMMENT && text.substring(it.start, it.endExclusive) == "# comment" })
    }
}
