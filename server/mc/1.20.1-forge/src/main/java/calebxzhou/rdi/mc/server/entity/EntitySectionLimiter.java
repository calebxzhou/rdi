package calebxzhou.rdi.mc.server.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = "rdi")
public final class EntitySectionLimiter {
    private static final int MAX_ENTITIES_PER_SECTION = Integer.getInteger("rdi.maxEntitiesPerSection", 1024);
    private static final Set<EntitySection<?>> PENDING_SECTIONS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private EntitySectionLimiter() {
    }

    public static void enqueue(EntitySection<?> section) {
        if (section.size() > MAX_ENTITIES_PER_SECTION) {
            PENDING_SECTIONS.add(section);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            drain();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_SECTIONS.clear();
    }

    public static void drain() {
        if (PENDING_SECTIONS.isEmpty()) {
            return;
        }

        List<EntitySection<?>> sections = List.copyOf(PENDING_SECTIONS);
        PENDING_SECTIONS.clear();
        for (EntitySection<?> section : sections) {
            trim(section);
        }
    }

    private static void trim(EntitySection<?> section) {
        while (section.size() > MAX_ENTITIES_PER_SECTION) {
            Entity candidate = section.getEntities()
                    .filter(Entity.class::isInstance)
                    .map(Entity.class::cast)
                    .filter(it -> !(it instanceof Player))
                    .filter(it -> !it.isRemoved())
                    .min(Comparator.comparingInt(Entity::getId))
                    .orElse(null);
            if (candidate == null) {
                return;
            }

            candidate.discard();
            if (!candidate.isRemoved()) {
                return;
            }
        }
    }
}
