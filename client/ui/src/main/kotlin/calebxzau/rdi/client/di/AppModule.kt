package calebxzau.rdi.client.di

import calebxzhou.rdi.client.database.ModpackLaunchOptionsStore
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionViewModel
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionRuntime
import calebxzau.rdi.client.ui.viewmodel.ModpackInfoGateway
import calebxzau.rdi.client.ui.viewmodel.ModpackInfoViewModel
import calebxzau.rdi.client.ui.viewmodel.SettingsModpackOptionRuntime
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionEditViewModel
import calebxzau.rdi.client.ui.viewmodel.RdiModpackInfoGateway
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadGateway
import calebxzau.rdi.client.ui.viewmodel.ModpackUploadViewModel
import calebxzau.rdi.client.ui.viewmodel.RdiModpackUploadGateway
import calebxzau.rdi.client.ui.viewmodel.RemoteModViewModel
import calebxzau.rdi.client.ui.viewmodel.RemoteModInfoViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(
    modCatalog: ModCatalog,
    modpackLaunchOptionsStore: ModpackLaunchOptionsStore,
) = module {
    single<ModCatalog> { modCatalog }
    single<ModpackLaunchOptionsStore> { modpackLaunchOptionsStore }
    single<ModpackOptionRuntime> { SettingsModpackOptionRuntime }
    single<ModpackInfoGateway> { RdiModpackInfoGateway(get()) }
    factory<ModpackUploadGateway> { RdiModpackUploadGateway(get()) }
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
    viewModel {
        ModpackUploadViewModel(gateway = get())
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
}
