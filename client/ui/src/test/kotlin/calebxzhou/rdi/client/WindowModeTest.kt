package calebxzhou.rdi.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowModeTest {
    @Test
    fun `solid window disables transparency by default`() {
        assertFalse(windowTransparent(solidWindow = true, jvmProperty = null))
        assertTrue(windowTransparent(solidWindow = false, jvmProperty = null))
    }

    @Test
    fun `jvm property overrides solid window setting`() {
        assertTrue(windowTransparent(solidWindow = true, jvmProperty = "true"))
        assertFalse(windowTransparent(solidWindow = false, jvmProperty = "false"))
    }
}
