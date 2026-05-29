package calebxzhou.rdi.mc.visky

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod

@Mod("rdi6sky")
//@EventBusSubscriber(modid = "rdi6sky")
class ViskyMain(bus: IEventBus) {
    init {
        ViskyItems.register(bus)
    }
}
