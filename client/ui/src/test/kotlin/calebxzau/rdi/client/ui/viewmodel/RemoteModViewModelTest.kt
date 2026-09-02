package calebxzau.rdi.client.ui.viewmodel

import calebxzau.rdi.client.modcatalog.CatalogOutcome
import calebxzau.rdi.client.modcatalog.CatalogSearchPage
import calebxzau.rdi.client.modcatalog.CatalogSearchRequest
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.ui.screen.RemoteModRoute
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import java.awt.EventQueue
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteModViewModelTest {
    @Test
    fun `view model search uses the route target`() = runBlocking {
        val request = CompletableDeferred<CatalogSearchRequest>()
        EventQueue.invokeAndWait {
            RemoteModViewModel(
                route = RemoteModRoute(requiredMcVer = "1.20.1", requiredLoader = "forge"),
                catalog = catalog { searchRequest ->
                    request.complete(searchRequest)
                    Result.success(CatalogOutcome(CatalogSearchPage(emptyList(), null, null)))
                },
            )
        }
        val searchRequest = withTimeout(5_000) { request.await() }

        assertEquals(McVersion.V201, searchRequest.target.minecraftVersion)
        assertEquals(ModLoader.forge, searchRequest.target.loader)
    }

    @Test
    fun `incompatible route target is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RemoteModRoute(requiredMcVer = "1.20.1", requiredLoader = "neoforge").toCatalogTarget()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun catalog(
        search: (CatalogSearchRequest) -> Result<CatalogOutcome<CatalogSearchPage>>,
    ): ModCatalog = Proxy.newProxyInstance(
        ModCatalog::class.java.classLoader,
        arrayOf(ModCatalog::class.java),
    ) { _, method, arguments ->
        when {
            method.name.startsWith("search") -> search(arguments?.first() as CatalogSearchRequest)
            method.name == "close" -> Unit
            method.name == "toString" -> "RemoteModViewModelTestCatalog"
            else -> error("Unexpected catalog call: ${method.name}")
        }
    } as ModCatalog
}
