package calebxzau.rdi.client

import kotlin.test.Test
import kotlin.test.assertNotNull

class BundledAssetsTest {
    @Test
    fun sharedAssetsAreAvailableFromRuntimeClasspath() {
        listOf(
            "assets/bg.avif",
            "assets/empty.ogg",
            "assets/icons/host.png",
            "icon.png",
            "mcmeta/1.21.1.json",
            "launcher_profiles.json",
            "forge-install-bootstrapper.jar",
            "overrides/fancymenu-options.txt"
        ).forEach { path ->
            assertNotNull(javaClass.classLoader.getResource(path), "Missing bundled asset: $path")
        }
    }
}
