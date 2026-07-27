package calebxzhou.rdi.master.service.host2

import calebxzhou.rdi.master.CONF
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureHost2() {
    val databaseProvider = DatabaseProvider(CONF.postgres)
    install(Koin) {
        slf4jLogger()
        modules(host2Module(databaseProvider))
    }
    launch { getKoin().get<Host2SetupService>().recover() }
    getKoin().get<Host2QuotaService>().start()
    monitor.subscribe(ApplicationStopping) {
        getKoin().get<Host2QuotaService>().close()
        getKoin().get<Host2SetupService>().close()
        databaseProvider.close()
    }
}

private fun host2Module(databaseProvider: DatabaseProvider) = module {
    single { databaseProvider }
    single { Host2Repository() }
    single { Host2Service(get(), get()) }
    single { Host2ModDownloadService() }
    single { Host2SetupService(get(), get(), get(), get()) }
    single { Host2ModsService(get(), get(), get(), get()) }
    single { Host2QuotaService(get(), get()) }
    single { Host2FileService(get()) }
}
