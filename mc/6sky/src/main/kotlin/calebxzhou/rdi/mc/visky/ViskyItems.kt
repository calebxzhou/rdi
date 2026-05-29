package calebxzhou.rdi.mc.visky

import calebxzhou.rdi.mc.visky.item.RainRocketItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ViskyItems {
    private const val MOD_ID = "rdi6sky"
    private val ITEMS = DeferredRegister.createItems(MOD_ID)

    val RAIN_ROCKET: DeferredItem<RainRocketItem> = ITEMS.register("rain_rocket", Supplier {
        RainRocketItem(Item.Properties().stacksTo(16))
    })

    fun register(bus: IEventBus) {
        ITEMS.register(bus)
        bus.addListener(::addCreativeItems)
    }

    private fun addCreativeItems(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ItemStack(RAIN_ROCKET.get()))
        }
    }
}
