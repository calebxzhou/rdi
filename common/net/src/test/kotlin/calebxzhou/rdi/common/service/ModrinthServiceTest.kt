package calebxzhou.rdi.common.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModrinthServiceTest {
    @Test
    fun `empty project request does not invoke fetcher`() = runBlocking {
        var calls = 0

        val result = ModrinthService.fetchMultipleProjects(emptyList()) {
            calls += 1
            emptyList()
        }

        assertTrue(result.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `large project request invokes fetcher once with distinct ordered ids`() = runBlocking {
        val expected = (0 until 101).map { "project-$it" }
        val calls = mutableListOf<List<String>>()

        val result = ModrinthService.fetchMultipleProjects(expected + expected.first()) { ids ->
            calls += ids
            emptyList()
        }

        assertTrue(result.isEmpty())
        assertEquals(listOf(expected), calls)
    }
}
