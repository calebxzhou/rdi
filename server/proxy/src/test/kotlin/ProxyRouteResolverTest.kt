import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.ProxyHostRoute
import calebxzhou.rdi.common.model.Response
import calebxzhou.rdi.prox.ProxyRouteResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyRouteResolverTest {
    @Test
    fun `returns route from master response`() = runBlocking {
        val resolver = ProxyRouteResolver(request = {
            Response(
                code = 0,
                msg = "",
                data = ProxyHostRoute(HostStatus.PLAYABLE, "10.0.0.1", 52341)
            )
        })

        val route = resolver.resolve(52341).getOrThrow()

        assertEquals("10.0.0.1", route.backendHost)
        assertEquals(52341, route.backendPort)
    }

    @Test
    fun `fails when master response has no route`() = runBlocking {
        val resolver = ProxyRouteResolver(request = {
            Response(code = -1, msg = "无此房间", data = null)
        })

        assertTrue(resolver.resolve(52341).isFailure)
    }

    @Test
    fun `fails when master route request times out`() = runBlocking {
        val resolver = ProxyRouteResolver(
            request = {
                delay(50)
                Response(code = 0, msg = "", data = null)
            },
            timeoutMillis = 1
        )

        assertTrue(resolver.resolve(52341).isFailure)
    }
}
