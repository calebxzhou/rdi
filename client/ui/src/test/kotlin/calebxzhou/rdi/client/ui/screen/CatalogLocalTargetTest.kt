package calebxzhou.rdi.client.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogLocalTargetTest {
    @Test
    fun `old local version id remains a legacy target`() {
        assertFalse(isDisabledCatalogLocalTargetKind(null))
        assertEquals(
            CatalogLocalTarget(CatalogLocalTargetKind.Legacy, "1.20.1-pack"),
            RemoteModRoute(
                requiredMcVer = "1.20.1",
                requiredLoader = "forge",
                targetLocalVersionId = "1.20.1-pack",
            ).localCatalogTarget(),
        )
    }

    @Test
    fun `explicit disabled kind never falls back to legacy version id`() {
        assertTrue(isDisabledCatalogLocalTargetKind("Modpack2"))
        assertNull(
            resolveCatalogLocalTarget(
                kindValue = "Modpack2",
                targetId = "018f-uuid",
                targetLocalVersionId = "legacy-field",
            )
        )
        assertNull(
            ResourceInfoRoute(
                type = ResourceInfoType.Shader.name,
                projectId = "iris",
                targetLocalKind = "Modpack2",
                targetLocalId = "018f-uuid",
                targetLocalVersionId = "legacy-field",
            ).localCatalogTarget()
        )
        val remoteModInfoRoute = RemoteModInfoRoute(
            platform = "MODRINTH",
            projectId = "iris",
            targetLocalKind = "Modpack2",
            targetLocalVersionId = "legacy-field",
        )
        assertTrue(remoteModInfoRoute.hasDisabledCatalogLocalTargetKind())
        assertNull(remoteModInfoRoute.localCatalogTarget())
    }
}
