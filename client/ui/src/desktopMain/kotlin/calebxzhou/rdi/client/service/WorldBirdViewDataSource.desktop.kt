package calebxzhou.rdi.client.service

actual fun createLocalWorldBirdViewDataSource(rootPath: String): WorldBirdViewDataSource? {
    return LocalWorldBirdViewDataSource(rootPath)
}
