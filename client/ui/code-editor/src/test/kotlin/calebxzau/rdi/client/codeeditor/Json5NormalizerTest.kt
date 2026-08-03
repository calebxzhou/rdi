package calebxzau.rdi.client.codeeditor

import calebxzau.rdi.client.codeeditor.normalizeJson5ToJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Json5NormalizerTest {
    @Test
    fun normalizesJson5Syntax() {
        assertEquals(
            "{\"name\":\"rdi\",\"count\":16}",
            normalizeJson5ToJson("{name: 'rdi', count: 0x10,}")
        )
    }

    @Test
    fun rejectsUnclosedComments() {
        assertFailsWith<IllegalArgumentException> {
            normalizeJson5ToJson("{/* comment")
        }
    }
}
