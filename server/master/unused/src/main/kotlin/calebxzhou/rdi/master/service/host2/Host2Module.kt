package calebxzhou.rdi.master.service.host2

import org.koin.dsl.module

internal fun host2Module() = module {
    single { Host2Repository() }
    single { Host2PackSourceService(get(), get(), get()) }
    single { Host2Service(get(), get(), get()) }
    single { Host2ModDownloadService() }
    single { Host2SetupService(get(), get(), get(), get(), get()) }
    single { Host2ContentsService(get(), get(), get(), get(), get(), get()) }
    single { Host2ClientPackService(get(), get(), get(), get()) }
    single { Host2QuotaService(get(), get()) }
    single { Host2FileService(get(), get(), get()) }
}
