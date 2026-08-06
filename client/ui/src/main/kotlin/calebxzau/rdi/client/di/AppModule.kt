package calebxzau.rdi.client.di

import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.ui.viewmodel.ModpackVersionEditViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(modCatalog: ModCatalog) = module {
    single<ModCatalog> { modCatalog }
    viewModel { parameters ->
        ModpackVersionEditViewModel(
            modCatalog = get(),
            modpackId = parameters.get(),
            verName = parameters.get(),
        )
    }
}
