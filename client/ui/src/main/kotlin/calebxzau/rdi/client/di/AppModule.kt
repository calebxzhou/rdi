package calebxzau.rdi.client.di

import calebxzhou.rdi.client.database.ModpackLaunchOptionsStore
import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionViewModel
import calebxzau.rdi.client.ui.viewmodel.ModpackOptionRuntime
import calebxzau.rdi.client.ui.viewmodel.SettingsModpackOptionRuntime
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionEditViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(
    modCatalog: ModCatalog,
    modpackLaunchOptionsStore: ModpackLaunchOptionsStore,
) = module {
    single<ModCatalog> { modCatalog }
    single<ModpackLaunchOptionsStore> { modpackLaunchOptionsStore }
    single<ModpackOptionRuntime> { SettingsModpackOptionRuntime }
    viewModel { parameters ->
        ModpackOptionViewModel(
            versionId = parameters.get(),
            store = get(),
            runtime = get(),
        )
    }
    viewModel { parameters ->
        ModpackVersionEditViewModel(
            modCatalog = get(),
            modpackId = parameters.get(),
            verName = parameters.get(),
        )
    }
}
