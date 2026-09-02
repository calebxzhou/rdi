package calebxzau.rdi.client.di

import calebxzhou.rdi.client.database.ModpackLaunchOptionsStore
import calebxzhou.rdi.client.database.MinecraftInstallationStore
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzhou.rdi.client.service.CatalogModpackArchiveCatalog
import calebxzhou.rdi.client.service.ModpackArchiveReader
import calebxzhou.rdi.client.service.ModpackArchiveContentResolutionSource
import calebxzhou.rdi.client.service.ModpackArchiveContentResolver
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionViewModel
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionRuntime
import calebxzau.rdi.client.ui.viewmodel.HostCreateGateway
import calebxzau.rdi.client.ui.viewmodel.HostCreateViewModel
import calebxzau.rdi.client.ui.viewmodel.HostGateway
import calebxzau.rdi.client.ui.viewmodel.HostInfoViewModel
import calebxzau.rdi.client.ui.viewmodel.HostModsViewModel
import calebxzau.rdi.client.ui.viewmodel.ModpackInfoGateway
import calebxzau.rdi.client.ui.viewmodel.ModpackInfoViewModel
import calebxzau.rdi.client.ui.viewmodel.SettingsModpackOptionRuntime
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionEditViewModel
import calebxzau.rdi.client.ui.viewmodel.RdiModpackInfoGateway
import calebxzau.rdi.client.ui.viewmodel.RdiHostCreateGateway
import calebxzau.rdi.client.ui.viewmodel.RdiHostGateway
import calebxzau.rdi.client.ui.viewmodel.RdiSettingsGateway
import calebxzau.rdi.client.ui.viewmodel.SettingsGateway
import calebxzau.rdi.client.ui.viewmodel.SettingsViewModel
import calebxzau.rdi.client.ui.viewmodel.HostListGateway
import calebxzau.rdi.client.ui.viewmodel.HostListViewModel
import calebxzau.rdi.client.ui.viewmodel.RdiHostListGateway
import calebxzau.rdi.client.ui.viewmodel.RemoteModViewModel
import calebxzau.rdi.client.ui.viewmodel.RemoteModInfoViewModel
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadGateway
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadViewModel
import calebxzau.rdi.client.ui.viewmodel.RdiModpackUploadGateway
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(
    modCatalog: ModCatalog,
    modpackLaunchOptionsStore: ModpackLaunchOptionsStore,
    minecraftInstallationStore: MinecraftInstallationStore,
) = module {
    single<ModCatalog> { modCatalog }
    single<ModpackLaunchOptionsStore> { modpackLaunchOptionsStore }
    single<MinecraftInstallationStore> { minecraftInstallationStore }
    factory<ModpackUploadGateway> { RdiModpackUploadGateway(get()) }
    single { ModpackArchiveReader(CatalogModpackArchiveCatalog(modCatalog)) }
    single<ModpackArchiveContentResolutionSource> { ModpackArchiveContentResolver(modCatalog) }
    single<ModpackOptionRuntime> { SettingsModpackOptionRuntime }
    single<HostCreateGateway> { RdiHostCreateGateway() }
    single<HostGateway> { RdiHostGateway() }
    single<HostListGateway> { RdiHostListGateway() }
    single<SettingsGateway> { RdiSettingsGateway() }
    single<ModpackInfoGateway> { RdiModpackInfoGateway(get()) }
    viewModel { parameters ->
        HostCreateViewModel(
            args = parameters.get(),
            gateway = get(),
        )
    }
    viewModel { parameters ->
        HostInfoViewModel(
            hostId = parameters.get(),
            gateway = get(),
        )
    }
    viewModel { parameters ->
        HostModsViewModel(
            hostId = parameters.get(),
            gateway = get(),
        )
    }
    viewModel { SettingsViewModel(gateway = get()) }
    viewModel { HostListViewModel(gateway = get(), useMockData = calebxzhou.rdi.client.Const.USE_MOCK_DATA) }
    viewModel { parameters ->
        ModpackOptionViewModel(
            versionId = parameters.get(),
            store = get(),
            runtime = get(),
        )
    }
    viewModel { parameters ->
        ModpackInfoViewModel(
            modpackId = parameters.get(),
            gateway = get(),
        )
    }
    viewModel { parameters ->
        ModpackVersionEditViewModel(
            modCatalog = get(),
            modpackId = parameters.get(),
            verName = parameters.get(),
        )
    }
    viewModel { parameters ->
        RemoteModViewModel(
            route = parameters.get(),
            catalog = get(),
        )
    }
    viewModel { parameters ->
        RemoteModInfoViewModel(
            route = parameters.get(),
            catalog = get(),
        )
    }
    viewModel { ModpackUploadViewModel(get()) }
}
