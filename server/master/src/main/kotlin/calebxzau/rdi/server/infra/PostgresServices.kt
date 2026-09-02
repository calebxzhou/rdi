package calebxzau.rdi.server.infra

import calebxzau.rdi.server.account.AccountMirrorService
import calebxzau.rdi.server.account.PgAccountRepo
import calebxzhou.rdi.master.CONF
import calebxzhou.rdi.master.lgr
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.PlayerService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configurePostgresServices() {
    val databaseProvider = DatabaseProvider(CONF.postgres)
    install(Koin) {
        slf4jLogger()
        modules(
            postgresModule(databaseProvider)
        )
    }

    val koin = getKoin()
    val accountMirrorService = koin.get<AccountMirrorService>()
    PlayerService.configureAccountMirror(accountMirrorService)
    launch(Dispatchers.IO) {
        accountMirrorService.scan(PlayerService.accountCol.find())
            .onSuccess { summary ->
                lgr.info {
                    "account mirror startup scan complete " +
                        "inserted=${summary.inserted} existing=${summary.existing} " +
                        "conflicts=${summary.conflicts} failed=${summary.failed}"
                }
            }
            .onFailure { error ->
                lgr.error(error) { "account mirror startup scan failed" }
            }
    }

    monitor.subscribe(ApplicationStopping) {
        databaseProvider.close()
    }
}

private fun postgresModule(databaseProvider: DatabaseProvider) = module {
    single { databaseProvider }
    single { PgAccountRepo() }
    single { AccountMirrorService(get(), get()) }
}
