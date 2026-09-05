package calebxzhou.rdi.client.ui.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RdiPack2ImportPickerTest {
    @Test
    fun `picker accepts only rdipack2 filenames`() {
        assertTrue(isRdiPack2ImportFileName("pack.rdipack2"))
        assertTrue(isRdiPack2ImportFileName("PACK.RDIPACK2"))
        assertTrue(isRdiPack2ImportFileName("pack.RdIpAcK2"))

        assertFalse(isRdiPack2ImportFileName("pack.rdimodpack"))
        assertFalse(isRdiPack2ImportFileName("pack.rdipack2.zip"))
        assertFalse(isRdiPack2ImportFileName("pack.zip"))
    }
}
