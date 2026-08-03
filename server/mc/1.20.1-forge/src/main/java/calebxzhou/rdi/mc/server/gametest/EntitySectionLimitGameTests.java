package calebxzhou.rdi.mc.server.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class EntitySectionLimitGameTests {
    private EntitySectionLimitGameTests() {
    }

    @GameTest(batch = "entitySectionSlime", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void slimeSplitDoesNotReenterEntityLimit(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        Slime parent = helper.spawn(EntityType.SLIME, position);
        parent.setSize(4, true);

        for (int i = 0; i < 7; i++) {
            helper.spawn(EntityType.MARKER, position);
        }

        parent.setHealth(0.0F);
        parent.remove(Entity.RemovalReason.KILLED);

        helper.runAfterDelay(1, () -> {
            if (!parent.isRemoved()) {
                helper.fail("The dying parent slime was not removed");
            }

            int slimeCount = helper.getEntities(EntityType.SLIME, position, 2.0).size();
            if (slimeCount < 2 || slimeCount > 4) {
                helper.fail("Expected 2-4 child slimes, found " + slimeCount);
            }

            int entityCount = helper.getLevel()
                    .getEntitiesOfClass(Entity.class, parent.getBoundingBox().inflate(1.0), entity -> true)
                    .size();
            if (entityCount > 8) {
                helper.fail("Entity section exceeded the test limit: " + entityCount);
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "entitySectionLifecycle", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void evictionUsesNormalEntityRemovalLifecycle(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 17, 1);
        MinecartChest chestMinecart = helper.spawn(EntityType.CHEST_MINECART, position);
        chestMinecart.setItem(0, new ItemStack(Items.DIAMOND));

        for (int i = 0; i < 8; i++) {
            helper.spawn(EntityType.MARKER, position);
        }

        helper.runAfterDelay(1, () -> {
            if (!chestMinecart.isRemoved()) {
                helper.fail("The oldest chest minecart was not evicted");
            }
            helper.assertItemEntityPresent(Items.DIAMOND, position, 2.0);
            helper.succeed();
        });
    }
}
