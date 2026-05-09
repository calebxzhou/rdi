package calebxzhou.rdi.mc.server.network;

import calebxzhou.rdi.mc.common2.mcp.RMcpBlockActionData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockBatchActionData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerMoveData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerPutData;
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftData;
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerMoveData;
import calebxzhou.rdi.mc.common2.mcp.RMcpPosData;
import calebxzhou.rdi.mc.common2.mcp.RErrorCode;
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryData;
import calebxzhou.rdi.mc.common2.mcp.RMcpItemPickupData;
import calebxzhou.rdi.mc.common2.mcp.RMcpMenuData;
import calebxzhou.rdi.mc.common2.mcp.RMcpMenuDropData;
import calebxzhou.rdi.mc.server.mcp.RMcpServerDataCodec211;
import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "rdi")
public final class RMcpServerNetwork {
    private static final Gson GSON = new Gson();
    private static final String BLOCK_ACTION_FORMAT = "block-action-v1";
    private static final String BLOCK_BATCH_ACTION_FORMAT = "block-batch-action-v1";
    private static final int BLOCK_BATCH_LIMIT = 512;
    private static final String CRAFT_FORMAT = "craft-v2";
    private static final String INVENTORY_FORMAT = "inventory-v1";
    private static final String MENU_FORMAT = "menu-v1";
    private static final String MENU_DROP_FORMAT = "menu-drop-v1";
    private static final String CONTAINER_FORMAT = "container-v1";
    private static final String CONTAINER_MOVE_FORMAT = "container-move-v1";
    private static final String CONTAINER_PUT_FORMAT = "container-put-v1";
    private static final String PLAYER_MOVE_FORMAT = "player-move-v1";
    private static final double PLAYER_MOVE_MAX_DISTANCE = 128.0D;
    private static final double BLOCK_ACTION_MAX_DISTANCE_SQR = 32.0D * 32.0D;
    private static final String ITEM_PICKUP_FORMAT = "item-pickup-v1";
    private static final int ITEM_PICKUP_LIMIT = 2048;
    private static final double ITEM_PICKUP_STILL_MOTION_SQR = 1.0;

    private RMcpServerNetwork() {
    }

    private static void replyError(IPayloadContext context, RMcpPayload payload, RErrorCode errorCode) {
        replyError(context, payload, errorCode.id());
    }

    private static void replyError(IPayloadContext context, RMcpPayload payload, String code) {
        context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), code, ""));
    }

    private static <T> void replyOk(IPayloadContext context, RMcpPayload payload, T data) {
        context.reply(new RMcpPayload(payload.requestId(), "response", payload.action(), "ok", GSON.toJson(data)));
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playBidirectional(RMcpPayload.TYPE, RMcpPayload.STREAM_CODEC, RMcpServerNetwork::handlePayload);
    }

    private static void handlePayload(RMcpPayload payload, IPayloadContext context) {
        if ("request".equals(payload.kind())) {
            context.enqueueWork(() -> handleRequest(payload, context));
        }
    }

    private static void handleRequest(RMcpPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            replyError(context, payload, RErrorCode.NO_PLAYER);
            return;
        }
        try {
            switch (payload.action()) {
                case "blockentity" -> handleBlockEntity(payload, context, player);
                case "harvest-tool" -> handleHarvestTool(payload, context, player);
                case "craft" -> handleCraft(payload, context, player);
                case "container" -> handleContainer(payload, context, player);
                case "menu" -> handleMenu(payload, context, player);
                case "menu-drop" -> handleMenuDrop(payload, context, player);
                case "container-move" -> handleContainerMove(payload, context, player);
                case "container-put" -> handleContainerPut(payload, context, player);
                case "place-block" -> handlePlaceBlock(payload, context, player);
                case "break-block" -> handleBreakBlock(payload, context, player);
                case "place-block-batch" -> handlePlaceBlockBatch(payload, context, player);
                case "break-block-batch" -> handleBreakBlockBatch(payload, context, player);
                case "place-block-box" -> handlePlaceBlockBox(payload, context, player);
                case "break-block-box" -> handleBreakBlockBox(payload, context, player);
                case "move-player" -> handleMovePlayer(payload, context, player);
                case "pickup-item-entity" -> handlePickupItemEntity(payload, context, player);
                default -> replyError(context, payload, RErrorCode.BAD_ACTION);
            }
        } catch (Exception e) {
            replyError(context, payload, RErrorCode.INTERNAL_ERROR);
        }
    }

    private static void handleBlockEntity(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockEntityRequest.class);
        if (request == null || request.dim() == null || request.dim().isBlank()) {
            replyError(context, payload, RErrorCode.BAD_REQUEST);
            return;
        }
        var level = player.serverLevel();
        var dim = level.dimension().location().toString();
        if (!dim.equals(request.dim())) {
            replyError(context, payload, RErrorCode.DIM_NOT_LOADED);
            return;
        }
        var blockEntity = level.getBlockEntity(new BlockPos(request.x(), request.y(), request.z()));
        if (blockEntity == null) {
            replyError(context, payload, RErrorCode.NO_BLOCK_ENTITY);
            return;
        }
        var data = RMcpServerDataCodec211.blockEntityData(dim, blockEntity, level.registryAccess());
        replyOk(context, payload, data);
    }

    private static void handleContainer(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), ContainerRequest.class);
        var pos = request == null ? null : parsePos(request.pos());
        if (pos == null) {
            replyError(context, payload, RErrorCode.BAD_POS);
            return;
        }
        var side = parseSide(request.side());
        if (side == SideParse.BAD) {
            replyError(context, payload, RErrorCode.BAD_SIDE);
            return;
        }
        var resolved = resolveContainer(player, pos, side.direction());
        if (!"ok".equals(resolved.code())) {
            replyError(context, payload, resolved.code());
            return;
        }
        replyOk(context, payload, containerData(resolved));
    }

    private static void handleMenu(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        replyOk(context, payload, menuData(player));
    }

    private static void handleMenuDrop(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), MenuDropRequest.class);
        if (request == null || request.slot() == null || request.count() <= 0) {
            replyError(context, payload, RErrorCode.BAD_REQUEST);
            return;
        }
        var menu = player.containerMenu;
        int slotIndex = request.slot();
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            replyError(context, payload, RErrorCode.BAD_SLOT);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            replyError(context, payload, RErrorCode.CARRIED_ITEM_NOT_EMPTY);
            return;
        }
        var slot = menu.slots.get(slotIndex);
        var beforeStack = slot.getItem().copy();
        if (beforeStack.isEmpty()) {
            replyError(context, payload, RErrorCode.EMPTY_SOURCE);
            return;
        }
        if (request.count() > beforeStack.getCount()) {
            replyError(context, payload, RErrorCode.BAD_COUNT);
            return;
        }
        if (!slot.mayPickup(player)) {
            replyError(context, payload, RErrorCode.ACTION_FAILED);
            return;
        }

        var registryAccess = player.serverLevel().registryAccess();
        var beforeSlot = menuSlotData(player, slotIndex, registryAccess);
        if (!request.dryRun()) {
            if (request.count() == beforeStack.getCount()) {
                menu.clicked(slotIndex, 1, ClickType.THROW, player);
            } else {
                for (int i = 0; i < request.count(); i++) {
                    menu.clicked(slotIndex, 0, ClickType.THROW, player);
                }
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
        var afterSlot = menuSlotData(player, slotIndex, registryAccess);
        var droppedCount = request.dryRun() ? 0 : request.count();
        var changed = droppedCount > 0 || beforeSlot.count() != afterSlot.count() || !java.util.Objects.equals(beforeSlot.snbt(), afterSlot.snbt());
        replyOk(context, payload, new RMcpMenuDropData(
                MENU_DROP_FORMAT,
                request.dryRun(),
                slotIndex,
                request.count(),
                droppedCount,
                changed,
                beforeSlot,
                afterSlot,
                menuData(player)
        ));
    }

    private static void handleContainerMove(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), ContainerMoveRequest.class);
        if (request == null || request.from() == null || request.to() == null || request.count() <= 0 || request.from().slot() == null) {
            replyError(context, payload, RErrorCode.BAD_REQUEST);
            return;
        }
        var fromPos = parsePos(request.from().pos());
        var toPos = parsePos(request.to().pos());
        if (fromPos == null || toPos == null) {
            replyError(context, payload, RErrorCode.BAD_POS);
            return;
        }
        var fromSide = parseSide(request.from().side());
        var toSide = parseSide(request.to().side());
        if (fromSide == SideParse.BAD || toSide == SideParse.BAD) {
            replyError(context, payload, RErrorCode.BAD_SIDE);
            return;
        }
        var from = resolveContainer(player, fromPos, fromSide.direction());
        if (!"ok".equals(from.code())) {
            replyError(context, payload, from.code());
            return;
        }
        var to = resolveContainer(player, toPos, toSide.direction());
        if (!"ok".equals(to.code())) {
            replyError(context, payload, to.code());
            return;
        }
        int fromSlot = request.from().slot();
        Integer toSlot = request.to().slot();
        if (!validSlot(from.handler(), fromSlot) || (toSlot != null && !validSlot(to.handler(), toSlot))) {
            replyError(context, payload, RErrorCode.BAD_SLOT);
            return;
        }
        if (sameEndpoint(from, fromSlot, to, toSlot)) {
            replyError(context, payload, RErrorCode.SAME_SLOT);
            return;
        }

        var extracted = from.handler().extractItem(fromSlot, request.count(), true);
        if (extracted.isEmpty()) {
            replyError(context, payload, RErrorCode.EMPTY_SOURCE);
            return;
        }
        var plan = insertionPlan(to.handler(), toSlot, extracted, sameContainer(from, to) ? fromSlot : null);
        if (plan.movedCount() <= 0) {
            replyError(context, payload, RErrorCode.TARGET_FULL);
            return;
        }

        ItemStack movedStack = extracted.copyWithCount(plan.movedCount());
        if (!request.dryRun()) {
            var actualExtracted = from.handler().extractItem(fromSlot, plan.movedCount(), false);
            var remaining = actualExtracted;
            for (var insert : plan.inserts()) {
                if (remaining.isEmpty()) {
                    break;
                }
                var stepStack = remaining.copyWithCount(Math.min(insert.count(), remaining.getCount()));
                var stepRemaining = to.handler().insertItem(insert.slot(), stepStack, false);
                remaining.shrink(stepStack.getCount() - stepRemaining.getCount());
            }
            markContainerChanged(from);
            markContainerChanged(to);
        }

        var data = new RMcpContainerMoveData(
                CONTAINER_MOVE_FORMAT,
                request.dryRun(),
                request.count(),
                plan.movedCount(),
                slotData(-1, movedStack, movedStack.getMaxStackSize(), true, from.level().registryAccess()),
                endpointData(from, fromSlot),
                endpointData(to, toSlot),
                containerData(from),
                containerData(to)
        );
        replyOk(context, payload, data);
    }

    private static void handleContainerPut(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), ContainerPutRequest.class);
        if (request == null || request.to() == null || request.fromInventorySlot() == null || request.count() <= 0) {
            replyError(context, payload, RErrorCode.BAD_REQUEST);
            return;
        }
        var toPos = parsePos(request.to().pos());
        if (toPos == null) {
            replyError(context, payload, RErrorCode.BAD_POS);
            return;
        }
        var toSide = parseSide(request.to().side());
        if (toSide == SideParse.BAD) {
            replyError(context, payload, RErrorCode.BAD_SIDE);
            return;
        }
        var to = resolveContainer(player, toPos, toSide.direction());
        if (!"ok".equals(to.code())) {
            replyError(context, payload, to.code());
            return;
        }
        int fromSlot = request.fromInventorySlot();
        var inventory = player.getInventory();
        if (fromSlot < 0 || fromSlot >= inventory.items.size()) {
            replyError(context, payload, RErrorCode.BAD_SLOT);
            return;
        }
        Integer toSlot = request.to().slot();
        if (toSlot != null && !validSlot(to.handler(), toSlot)) {
            replyError(context, payload, RErrorCode.BAD_SLOT);
            return;
        }
        if (!player.containerMenu.getCarried().isEmpty()) {
            replyError(context, payload, RErrorCode.CARRIED_ITEM_NOT_EMPTY);
            return;
        }

        var sourceStack = inventory.items.get(fromSlot);
        if (sourceStack.isEmpty()) {
            replyError(context, payload, RErrorCode.EMPTY_SOURCE);
            return;
        }
        if (request.count() > sourceStack.getCount()) {
            replyError(context, payload, RErrorCode.BAD_COUNT);
            return;
        }

        var registryAccess = player.serverLevel().registryAccess();
        var beforeInventorySlot = inventorySlotData(inventory, fromSlot, registryAccess);
        var requestedStack = sourceStack.copyWithCount(request.count());
        var plan = insertionPlan(to.handler(), toSlot, requestedStack, null);
        if (plan.movedCount() <= 0) {
            replyError(context, payload, RErrorCode.TARGET_FULL);
            return;
        }

        int movedCount = plan.movedCount();
        if (!request.dryRun()) {
            movedCount = 0;
            int remainingToMove = plan.movedCount();
            for (var insert : plan.inserts()) {
                if (remainingToMove <= 0 || sourceStack.isEmpty()) {
                    break;
                }
                var stepStack = sourceStack.copyWithCount(Math.min(insert.count(), remainingToMove));
                var stepRemaining = to.handler().insertItem(insert.slot(), stepStack, false);
                int inserted = stepStack.getCount() - stepRemaining.getCount();
                if (inserted > 0) {
                    sourceStack.shrink(inserted);
                    movedCount += inserted;
                    remainingToMove -= inserted;
                }
            }
            if (movedCount <= 0) {
                replyError(context, payload, RErrorCode.TARGET_FULL);
                return;
            }
            inventory.setChanged();
            markContainerChanged(to);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }

        var movedStack = requestedStack.copyWithCount(movedCount);
        replyOk(context, payload, new RMcpContainerPutData(
                CONTAINER_PUT_FORMAT,
                request.dryRun(),
                fromSlot,
                request.count(),
                movedCount,
                slotData(-1, movedStack, movedStack.getMaxStackSize(), true, registryAccess),
                beforeInventorySlot,
                inventorySlotData(inventory, fromSlot, registryAccess),
                endpointData(to, toSlot),
                inventoryData(player),
                containerData(to)
        ));
    }

    private static void handleHarvestTool(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), HarvestToolRequest.class);
        if (request == null || ((request.blockId() == null || request.blockId().isBlank()) && request.dim() == null)) {
            replyError(context, payload, RErrorCode.BAD_REQUEST);
            return;
        }
        var level = player.serverLevel();
        if (request.dim() != null && !request.dim().isBlank() && !level.dimension().location().toString().equals(request.dim())) {
            replyError(context, payload, RErrorCode.DIM_NOT_LOADED);
            return;
        }
        var data = RMcpServerDataCodec211.harvestToolData(level, player, request.blockId(), request.x(), request.y(), request.z());
        if (data == null) {
            replyError(context, payload, RErrorCode.BAD_BLOCK_ID);
            return;
        }
        replyOk(context, payload, data);
    }

    private static void handleCraft(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), CraftRequest.class);
        if (request == null || request.slots() == null || request.slots().isEmpty() || request.shape() == null || request.shape().isBlank()) {
            replyError(context, payload, RErrorCode.BAD_SHAPE);
            return;
        }
        if (request.outputSlot() < 0 || request.outputSlot() >= player.getInventory().items.size()) {
            replyError(context, payload, RErrorCode.BAD_SLOT);
            return;
        }
        if (request.times() <= 0 || request.times() > 64) {
            replyError(context, payload, RErrorCode.BAD_COUNT);
            return;
        }
        var plan = prepareCraftPlan(player, request);
        if (!"ok".equals(plan.code())) {
            replyError(context, payload, plan.code());
            return;
        }
        if (!request.dryRun()) {
            applyCraftPlan(player, plan);
        }
        var data = new RMcpCraftData(
                CRAFT_FORMAT,
                plan.recipeId(),
                plan.resultId(),
                plan.requestedCount(),
                request.dryRun() ? 0 : plan.craftedCount(),
                request.dryRun(),
                request.outputSlot(),
                inventoryData(player)
        );
        replyOk(context, payload, data);
    }

    private static void handlePlaceBlock(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockActionRequest.class);
        if (request == null) {
            replyError(context, payload, RErrorCode.BAD_POS);
            return;
        }
        var face = parseSide(request.face());
        if (face == SideParse.BAD) {
            replyError(context, payload, RErrorCode.BAD_SIDE);
            return;
        }
        var pos = new BlockPos(request.x(), request.y(), request.z());
        var level = player.serverLevel();
        var registryAccess = level.registryAccess();
        var mainHandBefore = mainHandData(player, registryAccess);
        var step = placeBlockAt(player, pos, face.direction());
        if (!"ok".equals(step.code())) {
            replyError(context, payload, step.code());
            return;
        }
        var data = blockActionData("place", step, mainHandBefore, player, registryAccess);
        replyOk(context, payload, data);
    }

    private static void handleBreakBlock(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockActionRequest.class);
        if (request == null) {
            replyError(context, payload, RErrorCode.BAD_POS);
            return;
        }
        var pos = new BlockPos(request.x(), request.y(), request.z());
        var level = player.serverLevel();
        var registryAccess = level.registryAccess();
        var mainHandBefore = mainHandData(player, registryAccess);
        var step = breakBlockAt(player, pos);
        if (!"ok".equals(step.code())) {
            replyError(context, payload, step.code());
            return;
        }
        var data = blockActionData("break", step, mainHandBefore, player, registryAccess);
        replyOk(context, payload, data);
    }

    private static void handlePlaceBlockBatch(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockBatchActionRequest.class);
        if (request == null || request.positions() == null || request.positions().isEmpty()) {
            replyError(context, payload, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (request.positions().size() > BLOCK_BATCH_LIMIT) {
            replyError(context, payload, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        var data = runBlockBatchAction(player, "place", request.positions(), true);
        replyOk(context, payload, data);
    }

    private static void handleBreakBlockBatch(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockBatchActionRequest.class);
        if (request == null || request.positions() == null || request.positions().isEmpty()) {
            replyError(context, payload, RErrorCode.BAD_POSITIONS);
            return;
        }
        if (request.positions().size() > BLOCK_BATCH_LIMIT) {
            replyError(context, payload, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        var data = runBlockBatchAction(player, "break", request.positions(), false);
        replyOk(context, payload, data);
    }

    private static void handlePlaceBlockBox(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockBoxActionRequest.class);
        if (request == null || request.from() == null || request.to() == null) {
            replyError(context, payload, RErrorCode.BAD_BOX);
            return;
        }
        if (boxBlockCount(request.from(), request.to()) > BLOCK_BATCH_LIMIT) {
            replyError(context, payload, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        var data = runBlockBatchAction(player, "place", expandBox(request.from(), request.to()), true);
        replyOk(context, payload, data);
    }

    private static void handleBreakBlockBox(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), BlockBoxActionRequest.class);
        if (request == null || request.from() == null || request.to() == null) {
            replyError(context, payload, RErrorCode.BAD_BOX);
            return;
        }
        if (boxBlockCount(request.from(), request.to()) > BLOCK_BATCH_LIMIT) {
            replyError(context, payload, RErrorCode.TOO_MANY_BLOCKS);
            return;
        }
        var data = runBlockBatchAction(player, "break", expandBox(request.from(), request.to()), false);
        replyOk(context, payload, data);
    }

    private static void handleMovePlayer(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), PlayerMoveRequest.class);
        if (request == null || request.x() == null || request.y() == null || request.z() == null
                || !Double.isFinite(request.x()) || !Double.isFinite(request.y()) || !Double.isFinite(request.z())) {
            replyError(context, payload, RErrorCode.BAD_POS);
            return;
        }
        var level = player.serverLevel();
        var targetPos = BlockPos.containing(request.x(), request.y(), request.z());
        if (level.isOutsideBuildHeight(targetPos)) {
            replyError(context, payload, RErrorCode.SECTION_OUT_OF_RANGE);
            return;
        }
        if (!level.isLoaded(targetPos)) {
            replyError(context, payload, RErrorCode.CHUNK_NOT_LOADED);
            return;
        }
        double distance = Math.sqrt(player.distanceToSqr(request.x(), request.y(), request.z()));
        if (distance > PLAYER_MOVE_MAX_DISTANCE) {
            replyError(context, payload, RErrorCode.TOO_FAR);
            return;
        }
        var from = posData(player);
        player.teleportTo(request.x(), request.y(), request.z());
        var to = posData(player);
        replyOk(context, payload, new RMcpPlayerMoveData(PLAYER_MOVE_FORMAT, true, from, to, distance, player.onGround()));
    }

    private static void handlePickupItemEntity(RMcpPayload payload, IPayloadContext context, ServerPlayer player) {
        var request = GSON.fromJson(payload.json(), ItemPickupRequest.class);
        if (request == null) {
            replyError(context, payload, RErrorCode.BAD_REQUEST);
            return;
        }
        double radius = request.radius() == null ? 64.0D : request.radius();
        int limit = request.limit() == null ? 256 : request.limit();
        if (!Double.isFinite(radius) || radius < 1.0D || radius > 64.0D) {
            replyError(context, payload, RErrorCode.BAD_RADIUS);
            return;
        }
        if (limit < 0 || limit > ITEM_PICKUP_LIMIT) {
            replyError(context, payload, RErrorCode.BAD_LIMIT);
            return;
        }
        var ids = request.ids() == null || request.ids().isEmpty()
                ? nearbyStillItemEntityIds(player, radius, limit)
                : request.ids();
        if (ids.size() > ITEM_PICKUP_LIMIT) {
            replyError(context, payload, RErrorCode.BAD_IDS);
            return;
        }
        var results = new ArrayList<RMcpItemPickupData.Result>();
        int pickedCount = 0;
        int failedCount = 0;
        for (var idText : ids) {
            var result = pickupItemEntity(player, idText, radius);
            if (result.picked()) {
                pickedCount++;
            }
            if (!"ok".equals(result.code())) {
                failedCount++;
            }
            results.add(result);
        }
        replyOk(context, payload, new RMcpItemPickupData(
                ITEM_PICKUP_FORMAT,
                ids.size(),
                pickedCount,
                failedCount,
                results,
                inventoryData(player)
        ));
    }

    private static List<String> nearbyStillItemEntityIds(ServerPlayer player, double radius, int limit) {
        if (limit == 0) {
            return List.of();
        }
        var radiusSqr = radius * radius;
        var items = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(radius),
                item -> !item.isRemoved() && !item.getItem().isEmpty()
                        && item.getDeltaMovement().lengthSqr() <= ITEM_PICKUP_STILL_MOTION_SQR
                        && player.distanceToSqr(item) <= radiusSqr
        );
        items.sort(Comparator.comparingDouble(item -> player.distanceToSqr(item)));
        var ids = new ArrayList<String>(Math.min(items.size(), limit));
        for (var item : items) {
            if (ids.size() >= limit) {
                break;
            }
            ids.add(item.getUUID().toString());
        }
        return ids;
    }

    private static RMcpItemPickupData.Result pickupItemEntity(ServerPlayer player, String idText, double radius) {
        UUID id;
        try {
            id = UUID.fromString(idText);
        } catch (Exception e) {
            return itemPickupResult(idText, RErrorCode.BAD_IDS.id(), false, 0, null, null, 0.0D, 0.0D);
        }
        var registryAccess = player.serverLevel().registryAccess();
        var entity = player.serverLevel().getEntity(id);
        if (entity == null || entity.isRemoved()) {
            return itemPickupResult(idText, RErrorCode.NO_ENTITY.id(), false, 0, null, null, 0.0D, 0.0D);
        }
        double distanceSqr = player.distanceToSqr(entity);
        double distance = Math.sqrt(distanceSqr);
        if (!(entity instanceof ItemEntity itemEntity)) {
            return itemPickupResult(idText, RErrorCode.NOT_ITEM_ENTITY.id(), false, 0, null, null, 0.0D, distance);
        }
        var stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return itemPickupResult(idText, RErrorCode.NO_ENTITY.id(), false, 0, null, null, 0.0D, distance);
        }
        var motionSqr = itemEntity.getDeltaMovement().lengthSqr();
        var beforeItem = itemSnbt(stack, registryAccess);
        if (distanceSqr > radius * radius) {
            return itemPickupResult(idText, RErrorCode.TOO_FAR.id(), false, 0, beforeItem, beforeItem, motionSqr, distance);
        }
        if (motionSqr > ITEM_PICKUP_STILL_MOTION_SQR) {
            return itemPickupResult(idText, RErrorCode.MOVING_ITEM_ENTITY.id(), false, 0, beforeItem, beforeItem, motionSqr, distance);
        }
        var before = stack.copy();
        player.getInventory().add(stack);
        int moved = before.getCount() - stack.getCount();
        if (moved <= 0) {
            return itemPickupResult(idText, RErrorCode.INVENTORY_FULL.id(), false, 0, beforeItem, beforeItem, motionSqr, distance);
        }
        player.take(itemEntity, moved);
        player.awardStat(Stats.ITEM_PICKED_UP.get(before.getItem()), moved);
        player.onItemPickup(itemEntity);
        if (stack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(stack);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        return itemPickupResult(
                idText,
                "ok",
                true,
                moved,
                beforeItem,
                stack.isEmpty() ? null : itemSnbt(stack, registryAccess),
                motionSqr,
                distance
        );
    }

    private static RMcpItemPickupData.Result itemPickupResult(String id, String code, boolean picked, int pickedCount, String beforeItem, String remainingItem, double motionSqr, double distance) {
        return new RMcpItemPickupData.Result(id, code, picked, pickedCount, beforeItem, remainingItem, motionSqr, distance);
    }

    private static RMcpBlockBatchActionData runBlockBatchAction(ServerPlayer player, String action, List<RMcpBlockPosData> positions, boolean place) {
        var level = player.serverLevel();
        var registryAccess = level.registryAccess();
        var mainHandBefore = mainHandData(player, registryAccess);
        var results = new ArrayList<RMcpBlockBatchActionData.Result>();
        int changedCount = 0;
        int failedCount = 0;
        for (var posData : positions) {
            BlockActionStep step;
            if (posData == null) {
                step = new BlockActionStep(null, RErrorCode.BAD_POS.id(), false, null, null);
            } else {
                var pos = blockPos(posData);
                step = place ? placeBlockAt(player, pos, null) : breakBlockAt(player, pos);
            }
            if (step.changed()) {
                changedCount++;
            }
            if (!"ok".equals(step.code())) {
                failedCount++;
            }
            results.add(step.batchResult());
        }
        return new RMcpBlockBatchActionData(
                BLOCK_BATCH_ACTION_FORMAT,
                action,
                positions.size(),
                changedCount,
                failedCount,
                results,
                mainHandBefore,
                mainHandData(player, registryAccess),
                inventoryData(player)
        );
    }

    private static BlockActionStep placeBlockAt(ServerPlayer player, BlockPos pos, Direction face) {
        var level = player.serverLevel();
        var check = checkBlockActionTarget(player, pos);
        if (!"ok".equals(check)) {
            return BlockActionStep.error(pos, check);
        }
        var beforeState = level.getBlockState(pos);
        var beforeBlockId = blockId(beforeState);
        if (!beforeState.isAir()) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.TARGET_NOT_AIR.id(), false, beforeBlockId, beforeBlockId);
        }
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.NO_PLACE_ITEM.id(), false, beforeBlockId, beforeBlockId);
        }
        var hitResult = placeHitResult(player, pos, face);
        if (!level.mayInteract(player, hitResult.getBlockPos())) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.PROTECTED.id(), false, beforeBlockId, beforeBlockId);
        }
        var result = blockItem.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hitResult));
        var afterBlockId = blockId(level.getBlockState(pos));
        if (!result.consumesAction() || level.getBlockState(pos).isAir()) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.PLACE_FAILED.id(), false, beforeBlockId, afterBlockId);
        }
        return new BlockActionStep(blockPosData(pos), "ok", !beforeBlockId.equals(afterBlockId), beforeBlockId, afterBlockId);
    }

    private static BlockActionStep breakBlockAt(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var check = checkBlockActionTarget(player, pos);
        if (!"ok".equals(check)) {
            return BlockActionStep.error(pos, check);
        }
        var beforeState = level.getBlockState(pos);
        var beforeBlockId = blockId(beforeState);
        if (beforeState.isAir()) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.NO_BLOCK.id(), false, beforeBlockId, beforeBlockId);
        }
        if (!player.gameMode.destroyBlock(pos)) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.BREAK_FAILED.id(), false, beforeBlockId, blockId(level.getBlockState(pos)));
        }
        var afterBlockId = blockId(level.getBlockState(pos));
        if (beforeBlockId.equals(afterBlockId)) {
            return new BlockActionStep(blockPosData(pos), RErrorCode.BREAK_FAILED.id(), false, beforeBlockId, afterBlockId);
        }
        return new BlockActionStep(blockPosData(pos), "ok", true, beforeBlockId, afterBlockId);
    }

    private static RMcpBlockActionData blockActionData(String action, BlockActionStep step, RMcpInventoryData.Item mainHandBefore, ServerPlayer player, HolderLookup.Provider registryAccess) {
        return new RMcpBlockActionData(
                BLOCK_ACTION_FORMAT,
                action,
                step.changed(),
                step.pos(),
                step.beforeBlockId(),
                step.afterBlockId(),
                mainHandBefore,
                mainHandData(player, registryAccess),
                inventoryData(player)
        );
    }

    private static ArrayList<RMcpBlockPosData> expandBox(RMcpBlockPosData from, RMcpBlockPosData to) {
        var positions = new ArrayList<RMcpBlockPosData>();
        int minX = Math.min(from.x(), to.x());
        int minY = Math.min(from.y(), to.y());
        int minZ = Math.min(from.z(), to.z());
        int maxX = Math.max(from.x(), to.x());
        int maxY = Math.max(from.y(), to.y());
        int maxZ = Math.max(from.z(), to.z());
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    positions.add(new RMcpBlockPosData(x, y, z));
                }
            }
        }
        return positions;
    }

    private static long boxBlockCount(RMcpBlockPosData from, RMcpBlockPosData to) {
        return (long) (Math.abs(from.x() - to.x()) + 1)
                * (Math.abs(from.y() - to.y()) + 1)
                * (Math.abs(from.z() - to.z()) + 1);
    }

    private static BlockPos blockPos(RMcpBlockPosData pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static RMcpBlockPosData blockPosData(BlockPos pos) {
        return new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ());
    }

    private static RMcpPosData posData(ServerPlayer player) {
        return new RMcpPosData(
                player.serverLevel().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    private static String checkBlockActionTarget(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!level.isLoaded(pos)) {
            return RErrorCode.CHUNK_NOT_LOADED.id();
        }
        if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > BLOCK_ACTION_MAX_DISTANCE_SQR) {
            return RErrorCode.TOO_FAR.id();
        }
        if (!level.mayInteract(player, pos)) {
            return RErrorCode.PROTECTED.id();
        }
        return "ok";
    }

    private static BlockHitResult placeHitResult(ServerPlayer player, BlockPos target, Direction explicitFace) {
        if (explicitFace != null) {
            return new BlockHitResult(Vec3.atCenterOf(target), explicitFace, target, false);
        }
        var level = player.serverLevel();
        Direction[] faces = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};
        for (var face : faces) {
            var clickedPos = target.relative(face.getOpposite());
            if (!level.isLoaded(clickedPos) || level.getBlockState(clickedPos).isAir() || !level.mayInteract(player, clickedPos)) {
                continue;
            }
            return new BlockHitResult(Vec3.atCenterOf(target), face, clickedPos, false);
        }
        return new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
    }

    private static CraftPlan prepareCraftPlan(ServerPlayer player, CraftRequest request) {
        var inputPlan = parseCraftInput(player, request);
        if (!"ok".equals(inputPlan.code())) {
            return CraftPlan.error(inputPlan.code());
        }
        var level = player.serverLevel();
        var input = inputPlan.input();
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        if (recipe == null) {
            return CraftPlan.error(RErrorCode.NO_MATCHING_RECIPE.id());
        }
        var result = recipe.value().assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            return CraftPlan.error(RErrorCode.CRAFT_FAILED.id());
        }
        int requestedCount = result.getCount() * request.times();
        if (requestedCount > result.getMaxStackSize()) {
            return CraftPlan.error(RErrorCode.RESULT_FULL.id());
        }
        var inventory = player.getInventory();
        for (var entry : inputPlan.requiredBySlot().entrySet()) {
            int required = entry.getValue() * request.times();
            if (inventory.items.get(entry.getKey()).getCount() < required) {
                return CraftPlan.error(RErrorCode.MISSING_INGREDIENTS.id());
            }
        }
        var simulatedItems = copyInventoryItems(player);
        for (var entry : inputPlan.requiredBySlot().entrySet()) {
            var stack = simulatedItems.get(entry.getKey());
            stack.shrink(entry.getValue() * request.times());
            if (stack.isEmpty()) {
                simulatedItems.set(entry.getKey(), ItemStack.EMPTY);
            }
        }
        var outputError = insertResultIntoSlot(simulatedItems, request.outputSlot(), result.copyWithCount(requestedCount), inputPlan.requiredBySlot().containsKey(request.outputSlot()));
        if (outputError != null) {
            return CraftPlan.error(outputError);
        }
        List<ItemStack> remainingItems;
        CommonHooks.setCraftingPlayer(player);
        try {
            remainingItems = recipe.value().getRemainingItems(input);
        } finally {
            CommonHooks.setCraftingPlayer(null);
        }
        for (int craftIndex = 0; craftIndex < request.times(); craftIndex++) {
            for (int index = 0; index < remainingItems.size(); index++) {
                var remaining = remainingItems.get(index);
                if (!remaining.isEmpty() && !insertRemainder(simulatedItems, inputPlan.sourceSlots()[index], remaining.copy())) {
                    return CraftPlan.error(RErrorCode.RESULT_FULL.id());
                }
            }
        }
        return new CraftPlan(
                "ok",
                recipe,
                input,
                simulatedItems,
                recipe.id().toString(),
                itemId(result),
                requestedCount,
                requestedCount,
                result.copyWithCount(requestedCount)
        );
    }

    private static ParsedCraftInput parseCraftInput(ServerPlayer player, CraftRequest request) {
        var rows = request.shape().split("\\|", -1);
        if (rows.length == 0 || rows.length > 3 || rows[0].isEmpty() || rows[0].length() > 3) {
            return ParsedCraftInput.error(RErrorCode.BAD_SHAPE.id());
        }
        int width = rows[0].length();
        for (var row : rows) {
            if (row.length() != width) {
                return ParsedCraftInput.error(RErrorCode.BAD_SHAPE.id());
            }
        }
        var inventory = player.getInventory();
        var items = new ArrayList<ItemStack>(width * rows.length);
        var sourceSlotByOriginalIndex = new int[width * rows.length];
        var requiredBySlot = new LinkedHashMap<Integer, Integer>();
        for (int index = 0; index < sourceSlotByOriginalIndex.length; index++) {
            sourceSlotByOriginalIndex[index] = -1;
        }
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                char symbol = rows[y].charAt(x);
                if (symbol == '.' || symbol == ' ') {
                    items.add(ItemStack.EMPTY);
                    continue;
                }
                var sourceSlot = request.slots().get(Character.toString(symbol));
                if (sourceSlot == null || sourceSlot < 0 || sourceSlot >= inventory.items.size()) {
                    return ParsedCraftInput.error(RErrorCode.BAD_SHAPE.id());
                }
                var sourceStack = inventory.items.get(sourceSlot);
                if (sourceStack.isEmpty()) {
                    return ParsedCraftInput.error(RErrorCode.MISSING_INGREDIENTS.id());
                }
                items.add(sourceStack.copyWithCount(1));
                sourceSlotByOriginalIndex[index] = sourceSlot;
                requiredBySlot.merge(sourceSlot, 1, Integer::sum);
            }
        }
        if (requiredBySlot.isEmpty()) {
            return ParsedCraftInput.error(RErrorCode.BAD_SHAPE.id());
        }
        var positioned = CraftingInput.ofPositioned(width, rows.length, items);
        var input = positioned.input();
        if (input.isEmpty()) {
            return ParsedCraftInput.error(RErrorCode.BAD_SHAPE.id());
        }
        var sourceSlots = new int[input.size()];
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int originalIndex = x + positioned.left() + (y + positioned.top()) * width;
                sourceSlots[inputIndex] = sourceSlotByOriginalIndex[originalIndex];
            }
        }
        return new ParsedCraftInput("ok", input, sourceSlots, requiredBySlot);
    }

    private static void applyCraftPlan(ServerPlayer player, CraftPlan plan) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < plan.simulatedItems().size(); slot++) {
            inventory.items.set(slot, plan.simulatedItems().get(slot));
        }
        var crafted = plan.resultStack().copy();
        crafted.onCraftedBy(player.serverLevel(), player, plan.craftedCount());
        player.awardRecipes(List.of(plan.recipe()));
        player.triggerRecipeCrafted(plan.recipe(), plan.input().items());
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static ArrayList<ItemStack> copyInventoryItems(ServerPlayer player) {
        var copied = new ArrayList<ItemStack>(player.getInventory().items.size());
        for (var stack : player.getInventory().items) {
            copied.add(stack.copy());
        }
        return copied;
    }

    private static String insertResultIntoSlot(List<ItemStack> items, int slot, ItemStack stack, boolean outputWasInput) {
        var target = items.get(slot);
        if (target.isEmpty()) {
            items.set(slot, stack.copy());
            return null;
        }
        if (!ItemStack.isSameItemSameComponents(target, stack)) {
            return outputWasInput ? RErrorCode.SAME_SLOT.id() : RErrorCode.RESULT_FULL.id();
        }
        int limit = Math.min(target.getMaxStackSize(), stack.getMaxStackSize());
        if (limit - target.getCount() < stack.getCount()) {
            return RErrorCode.RESULT_FULL.id();
        }
        target.grow(stack.getCount());
        return null;
    }

    private static boolean insertRemainder(List<ItemStack> items, int preferredSlot, ItemStack stack) {
        var remaining = stack.copy();
        if (preferredSlot >= 0) {
            insertPartial(items, preferredSlot, remaining);
        }
        for (int slot = 0; slot < items.size() && !remaining.isEmpty(); slot++) {
            if (slot != preferredSlot) {
                insertPartial(items, slot, remaining);
            }
        }
        return remaining.isEmpty();
    }

    private static void insertPartial(List<ItemStack> items, int slot, ItemStack remaining) {
        var target = items.get(slot);
        if (target.isEmpty()) {
            int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            items.set(slot, remaining.copyWithCount(moved));
            remaining.shrink(moved);
            return;
        }
        if (!ItemStack.isSameItemSameComponents(target, remaining)) {
            return;
        }
        int moved = Math.min(remaining.getCount(), Math.min(target.getMaxStackSize(), remaining.getMaxStackSize()) - target.getCount());
        if (moved > 0) {
            target.grow(moved);
            remaining.shrink(moved);
        }
    }

    private static ContainerResolve resolveContainer(ServerPlayer player, ParsedPos pos, Direction side) {
        var level = player.serverLevel();
        if (!level.dimension().location().toString().equals(pos.dim())) {
            return ContainerResolve.error(RErrorCode.DIM_NOT_LOADED.id());
        }
        var blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
        if (!level.isLoaded(blockPos)) {
            return ContainerResolve.error(RErrorCode.CHUNK_NOT_LOADED.id());
        }
        if (!player.canInteractWithBlock(blockPos, 8.0)) {
            return ContainerResolve.error(RErrorCode.TOO_FAR.id());
        }
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, blockPos, side);
        if (handler == null) {
            return ContainerResolve.error(RErrorCode.NO_ITEM_HANDLER.id());
        }
        return new ContainerResolve("ok", level, blockPos, side, handler);
    }

    private static RMcpContainerData containerData(ContainerResolve container) {
        var items = new ArrayList<RMcpContainerData.Slot>();
        for (int slot = 0; slot < container.handler().getSlots(); slot++) {
            var stack = container.handler().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.add(slotData(slot, stack, container.handler().getSlotLimit(slot), container.handler().isItemValid(slot, stack), container.level().registryAccess()));
            }
        }
        return new RMcpContainerData(
                CONTAINER_FORMAT,
                container.level().dimension().location().toString(),
                new RMcpBlockPosData(container.pos().getX(), container.pos().getY(), container.pos().getZ()),
                sideName(container.side()),
                container.handler().getSlots(),
                items
        );
    }

    private static RMcpMenuData menuData(ServerPlayer player) {
        var menu = player.containerMenu;
        var registryAccess = player.serverLevel().registryAccess();
        var slots = new ArrayList<RMcpMenuData.Slot>(menu.slots.size());
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            slots.add(menuSlotData(player, slot, registryAccess));
        }
        return new RMcpMenuData(
                MENU_FORMAT,
                player.serverLevel().dimension().location().toString(),
                menu.containerId,
                menu.getClass().getName(),
                menu == player.inventoryMenu,
                carriedMenuSlot(menu.getCarried(), registryAccess),
                menu.slots.size(),
                slots
        );
    }

    private static RMcpMenuData.Slot menuSlotData(ServerPlayer player, int slotIndex, HolderLookup.Provider registryAccess) {
        var slot = player.containerMenu.slots.get(slotIndex);
        var stack = slot.getItem();
        if (stack.isEmpty()) {
            return new RMcpMenuData.Slot(slotIndex, true, false, null, 0, 0, null);
        }
        return new RMcpMenuData.Slot(
                slotIndex,
                false,
                slot.mayPickup(player),
                itemId(stack),
                stack.getCount(),
                slot.getMaxStackSize(stack),
                stack.saveOptional(registryAccess).toString()
        );
    }

    private static RMcpMenuData.Slot carriedMenuSlot(ItemStack stack, HolderLookup.Provider registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return new RMcpMenuData.Slot(
                -1,
                false,
                false,
                itemId(stack),
                stack.getCount(),
                stack.getMaxStackSize(),
                stack.saveOptional(registryAccess).toString()
        );
    }

    private static RMcpContainerData.Slot slotData(int slot, ItemStack stack, int limit, boolean canInsert, net.minecraft.core.HolderLookup.Provider registryAccess) {
        return new RMcpContainerData.Slot(
                slot,
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                limit,
                canInsert,
                stack.saveOptional(registryAccess).toString()
        );
    }

    private static RMcpContainerMoveData.Endpoint endpointData(ContainerResolve container, Integer slot) {
        return new RMcpContainerMoveData.Endpoint(
                container.level().dimension().location().toString(),
                new RMcpBlockPosData(container.pos().getX(), container.pos().getY(), container.pos().getZ()),
                sideName(container.side()),
                slot
        );
    }

    private static InsertPlan insertionPlan(IItemHandler target, Integer targetSlot, ItemStack stack, Integer excludedSlot) {
        var inserts = new ArrayList<InsertStep>();
        var remaining = stack.copy();
        if (targetSlot != null) {
            remaining = simulateInsert(target, targetSlot, remaining, excludedSlot, inserts);
        } else {
            for (int slot = 0; slot < target.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = simulateInsert(target, slot, remaining, excludedSlot, inserts);
            }
        }
        return new InsertPlan(stack.getCount() - remaining.getCount(), inserts);
    }

    private static ItemStack simulateInsert(IItemHandler target, int slot, ItemStack stack, Integer excludedSlot, ArrayList<InsertStep> inserts) {
        if (excludedSlot != null && excludedSlot == slot) {
            return stack;
        }
        var before = stack.getCount();
        var remaining = target.insertItem(slot, stack, true);
        var moved = before - remaining.getCount();
        if (moved > 0) {
            inserts.add(new InsertStep(slot, moved));
        }
        return remaining;
    }

    private static void markContainerChanged(ContainerResolve container) {
        var blockEntity = container.level().getBlockEntity(container.pos());
        if (blockEntity != null) {
            blockEntity.setChanged();
            var state = container.level().getBlockState(container.pos());
            container.level().sendBlockUpdated(container.pos(), state, state, 3);
        }
    }

    private static RMcpInventoryData inventoryData(ServerPlayer player) {
        var inventory = player.getInventory();
        var registryAccess = player.serverLevel().registryAccess();
        return new RMcpInventoryData(
                INVENTORY_FORMAT,
                player.serverLevel().dimension().location().toString(),
                inventory.selected,
                compactItem("hotbar", inventory.selected, inventory.selected, inventory.getSelected(), registryAccess),
                compactItemRange("hotbar", inventory.items, 0, 9, registryAccess),
                compactItemRange("inventory", inventory.items, 9, inventory.items.size(), registryAccess),
                compactItemRange("armor", inventory.armor, 0, inventory.armor.size(), registryAccess),
                compactItemRange("offhand", inventory.offhand, 0, inventory.offhand.size(), registryAccess),
                inventorySummary(allInventoryStacks(player))
        );
    }

    private static List<RMcpInventoryData.Item> compactItemRange(String section, List<ItemStack> stacks, int from, int to, HolderLookup.Provider registryAccess) {
        var items = new ArrayList<RMcpInventoryData.Item>();
        for (int slot = from; slot < to; slot++) {
            var item = compactItem(section, slot, "hotbar".equals(section) ? slot : null, stacks.get(slot), registryAccess);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static RMcpInventoryData.Item compactItem(String section, int slot, Integer hotbarSlot, ItemStack stack, HolderLookup.Provider registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return new RMcpInventoryData.Item(
                section,
                slot,
                hotbarSlot,
                itemId(stack),
                stack.getCount(),
                stack.saveOptional(registryAccess).toString()
        );
    }

    private static RMcpInventoryData.Item inventorySlotData(net.minecraft.world.entity.player.Inventory inventory, int slot, HolderLookup.Provider registryAccess) {
        return compactItem(slot < 9 ? "hotbar" : "inventory", slot, slot < 9 ? slot : null, inventory.items.get(slot), registryAccess);
    }

    private static RMcpInventoryData.Summary inventorySummary(List<ItemStack> stacks) {
        var counts = new LinkedHashMap<String, Integer>();
        int occupiedSlots = 0;
        int totalItems = 0;
        boolean hasFood = false;
        boolean hasTool = false;
        boolean hasWeapon = false;
        boolean hasBlock = false;
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            var id = itemId(stack);
            occupiedSlots++;
            totalItems += stack.getCount();
            counts.merge(id, stack.getCount(), Integer::sum);
            hasFood = hasFood || stack.get(DataComponents.FOOD) != null;
            hasTool = hasTool || isToolItem(id);
            hasWeapon = hasWeapon || isWeaponItem(id);
            hasBlock = hasBlock || stack.getItem() instanceof BlockItem;
        }
        var topItems = counts.entrySet().stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getValue(), left.getValue());
                    return countCompare != 0 ? countCompare : left.getKey().compareTo(right.getKey());
                })
                .limit(12)
                .map(entry -> new RMcpInventoryData.ItemCount(entry.getKey(), entry.getValue()))
                .toList();
        return new RMcpInventoryData.Summary(
                occupiedSlots,
                stacks.size() - occupiedSlots,
                totalItems,
                topItems,
                hasFood,
                hasTool,
                hasWeapon,
                hasBlock
        );
    }

    private static List<ItemStack> allInventoryStacks(ServerPlayer player) {
        var inventory = player.getInventory();
        var stacks = new ArrayList<ItemStack>();
        stacks.addAll(inventory.items);
        stacks.addAll(inventory.armor);
        stacks.addAll(inventory.offhand);
        return stacks;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String itemSnbt(ItemStack stack, HolderLookup.Provider registryAccess) {
        return stack == null || stack.isEmpty() ? null : stack.saveOptional(registryAccess).toString();
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static RMcpInventoryData.Item mainHandData(ServerPlayer player, HolderLookup.Provider registryAccess) {
        var inventory = player.getInventory();
        return compactItem("hotbar", inventory.selected, inventory.selected, player.getMainHandItem(), registryAccess);
    }

    private static boolean isToolItem(String id) {
        return id.endsWith("_pickaxe")
                || id.endsWith("_axe")
                || id.endsWith("_shovel")
                || id.endsWith("_hoe")
                || id.endsWith(":shears")
                || id.endsWith(":flint_and_steel")
                || id.endsWith(":bucket")
                || id.endsWith("_bucket");
    }

    private static boolean isWeaponItem(String id) {
        return id.endsWith("_sword")
                || id.endsWith(":bow")
                || id.endsWith(":crossbow")
                || id.endsWith(":trident")
                || id.endsWith(":mace");
    }

    private static boolean validSlot(IItemHandler handler, int slot) {
        return slot >= 0 && slot < handler.getSlots();
    }

    private static boolean sameEndpoint(ContainerResolve from, int fromSlot, ContainerResolve to, Integer toSlot) {
        return toSlot != null && sameContainer(from, to) && fromSlot == toSlot;
    }

    private static boolean sameContainer(ContainerResolve from, ContainerResolve to) {
        return from.level() == to.level() && from.pos().equals(to.pos()) && from.side() == to.side();
    }

    private static String sideName(Direction direction) {
        return direction == null ? null : direction.getSerializedName();
    }

    private static SideParse parseSide(String text) {
        if (text == null || text.isBlank()) {
            return new SideParse(null, false);
        }
        var direction = Direction.byName(text.trim().toLowerCase());
        return direction == null ? SideParse.BAD : new SideParse(direction, false);
    }

    private static ParsedPos parsePos(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var parts = text.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            var dim = parts[0].trim();
            if (dim.isEmpty()) {
                return null;
            }
            return new ParsedPos(dim, Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record BlockEntityRequest(String dim, int x, int y, int z) {
    }

    private record HarvestToolRequest(String blockId, String dim, Integer x, Integer y, Integer z) {
    }

    private record CraftRequest(Map<String, Integer> slots, String shape, int outputSlot, int times, boolean dryRun) {
    }

    private record BlockActionRequest(int x, int y, int z, String face) {
    }

    private record PlayerMoveRequest(Double x, Double y, Double z) {
    }

    private record ItemPickupRequest(List<String> ids, Double radius, Integer limit) {
    }

    private record BlockBatchActionRequest(List<RMcpBlockPosData> positions) {
    }

    private record BlockBoxActionRequest(RMcpBlockPosData from, RMcpBlockPosData to) {
    }

    private record BlockActionStep(RMcpBlockPosData pos, String code, boolean changed, String beforeBlockId, String afterBlockId) {
        private static BlockActionStep error(BlockPos pos, String code) {
            return new BlockActionStep(blockPosData(pos), code, false, null, null);
        }

        private RMcpBlockBatchActionData.Result batchResult() {
            return new RMcpBlockBatchActionData.Result(pos, code, changed, beforeBlockId, afterBlockId);
        }
    }

    private record ContainerRequest(String pos, String side) {
    }

    private record MenuDropRequest(Integer slot, int count, boolean dryRun) {
    }

    private record ContainerMoveRequest(ContainerEndpointRequest from, ContainerEndpointRequest to, int count, boolean dryRun) {
    }

    private record ContainerPutRequest(Integer fromInventorySlot, ContainerEndpointRequest to, int count, boolean dryRun) {
    }

    private record ContainerEndpointRequest(String pos, String side, Integer slot) {
    }

    private record ParsedPos(String dim, int x, int y, int z) {
    }

    private record SideParse(Direction direction, boolean bad) {
        private static final SideParse BAD = new SideParse(null, true);
    }

    private record ContainerResolve(String code, net.minecraft.server.level.ServerLevel level, BlockPos pos, Direction side, IItemHandler handler) {
        private static ContainerResolve error(String code) {
            return new ContainerResolve(code, null, null, null, null);
        }
    }

    private record InsertPlan(int movedCount, ArrayList<InsertStep> inserts) {
    }

    private record InsertStep(int slot, int count) {
    }

    private record ParsedCraftInput(String code, CraftingInput input, int[] sourceSlots, LinkedHashMap<Integer, Integer> requiredBySlot) {
        private static ParsedCraftInput error(String code) {
            return new ParsedCraftInput(code, null, null, null);
        }
    }

    private record CraftPlan(
            String code,
            RecipeHolder<CraftingRecipe> recipe,
            CraftingInput input,
            List<ItemStack> simulatedItems,
            String recipeId,
            String resultId,
            int requestedCount,
            int craftedCount,
            ItemStack resultStack
    ) {
        private static CraftPlan error(String code) {
            return new CraftPlan(code, null, null, null, null, null, 0, 0, ItemStack.EMPTY);
        }
    }
}
