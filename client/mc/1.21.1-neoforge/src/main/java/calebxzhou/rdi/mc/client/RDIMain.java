package calebxzhou.rdi.mc.client;

import calebxzhou.rdi.mc.client.mcp.RMcpMcDataCodec211;
import calebxzhou.rdi.mc.client.network.RMcpClientBridge;
import calebxzhou.rdi.mc.client.rcmd.RcmdClientCommands;
import calebxzhou.rdi.mc.client.rcmd.RcmdRecipeCodec211;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockActionData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockBatchActionData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockStateData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockStateBatchData;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockStateEntryData;
import calebxzhou.rdi.mc.common2.mcp.RMcpChunkSemanticData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerData;
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerMoveData;
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftData;
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftingOpenData;
import calebxzhou.rdi.mc.common2.mcp.RMcpEndpointException;
import calebxzhou.rdi.mc.common2.mcp.RErrorCode;
import calebxzhou.rdi.mc.common2.mcp.RMcpEntityData;
import calebxzhou.rdi.mc.common2.mcp.RMcpEntityDetailData;
import calebxzhou.rdi.mc.common2.mcp.RMcpFluidData;
import calebxzhou.rdi.mc.common2.mcp.RMcpGameConnector;
import calebxzhou.rdi.mc.common2.mcp.RMcpHarvestToolData;
import calebxzhou.rdi.mc.common2.mcp.RMHttpServer;
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryData;
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryMoveData;
import calebxzhou.rdi.mc.common2.mcp.RMcpInventorySwapData;
import calebxzhou.rdi.mc.common2.mcp.RMcpLangKeyIndex;
import calebxzhou.rdi.mc.common2.mcp.RMcpNearbyEntitiesData;
import calebxzhou.rdi.mc.common2.mcp.RMcpNearbyResourcesData;
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerData;
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerDetailData;
import calebxzhou.rdi.mc.common2.mcp.RMcpPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpSectionSemanticData;
import calebxzhou.rdi.mc.common2.mcp.RMcpSituationData;
import calebxzhou.rdi.mc.common2.mcp.RMcpStaringBlockData;
import calebxzhou.rdi.mc.common2.mcp.RMcpTestData;
import calebxzhou.rdi.mc.common.RDI;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;
import com.google.common.net.HostAndPort;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.pipeline.RenderCall;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.commands.Commands;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static calebxzhou.rdi.mc.common.RDI.*;

/**
 * calebxzhou @ 2026-01-10 22:33
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi",value = Dist.CLIENT)
public class RDIMain {
    private static final Logger LGR = LoggerFactory.getLogger("rdi-mcp");
    private static final String SECTION_FORMAT = "section-semantic-v1";
    private static final String CHUNK_FORMAT = "chunk-semantic-v1";
    private static final String NEARBY_RESOURCES_FORMAT = "nearby-resources-v1";
    private static final String NEARBY_ENTITIES_FORMAT = "nearby-entities-v1";
    private static final String INVENTORY_FORMAT = "inventory-v1";
    private static final String INVENTORY_MOVE_FORMAT = "inventory-move-v1";
    private static final String INVENTORY_SWAP_FORMAT = "inventory-swap-v1";
    private static final String SITUATION_FORMAT = "situation-v1";
    private static final String GRID_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final ExecutorService SCREENSHOT_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "rdi-mcp-screenshot.md");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile LangKeyIndexCache langKeyIndex;

    public RDIMain() {
        try {
            RMHttpServer.start(HOST_PORT,new RMcpGameConnector() {
                @Override
                public boolean playerInWorld() {
                    var minecraft = Minecraft.getInstance();
                    return minecraft.level != null && minecraft.player != null;
                }

                @Override
                public RMcpTestData testData() {
                    return new RMcpTestData("rdi", "1.21.1", "client", Minecraft.getInstance().level != null);
                }

                @Override
                public RMcpPosData posData() {
                    var player = Minecraft.getInstance().player;
                    if (player == null) {
                        return null;
                    }
                    var dim = player.level().dimension().location().toString();
                    return new RMcpPosData(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                }

                @Override
                public RMcpInventoryData inventoryData() {
                    return RDIMain.inventoryData(Minecraft.getInstance());
                }

                @Override
                public RMcpInventorySwapData swapInventorySlots(String from, String to, boolean dryRun) {
                    return RDIMain.swapInventorySlots(Minecraft.getInstance(), from, to, dryRun);
                }

                @Override
                public RMcpInventoryMoveData moveInventoryItems(String from, String to, int count, boolean dryRun) {
                    return RDIMain.moveInventoryItems(Minecraft.getInstance(), from, to, count, dryRun);
                }

                @Override
                public RMcpCraftingOpenData openCrafting(int radius, boolean dryRun) {
                    return RMcpClientBridge.requestOpenCrafting(radius, dryRun);
                }

                @Override
                public RMcpCraftData craft(Map<String, Integer> slots, String shape, int outputSlot, int times, boolean dryRun) {
                    return RMcpClientBridge.requestCraft(slots, shape, outputSlot, times, dryRun);
                }

                @Override
                public RMcpSituationData situationData(double entityRadius, int resourceChunkRadius, int resourceSectionRadius) {
                    return RDIMain.situationData(Minecraft.getInstance(), entityRadius, resourceChunkRadius, resourceSectionRadius);
                }

                @Override
                public Map<String, Object> mainHandItemData() {
                    var minecraft = Minecraft.getInstance();
                    var player = minecraft.player;
                    if (minecraft.level == null || player == null) {
                        return null;
                    }
                    return RMcpMcDataCodec211.itemStackToSnbtMap(player.getMainHandItem(), minecraft.level.registryAccess());
                }

                @Override
                public CompletableFuture<byte[]> screenshotPngData() {
                    var minecraft = Minecraft.getInstance();
                    var future = new CompletableFuture<byte[]>();
                    RenderCall capture = () -> {
                        try {
                            var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
                            SCREENSHOT_EXECUTOR.execute(() -> {
                                try (image) {
                                    future.complete(image.asByteArray());
                                } catch (Exception e) {
                                    future.completeExceptionally(e);
                                }
                            });
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    };
                    if (RenderSystem.isOnRenderThread()) {
                        capture.execute();
                    } else {
                        RenderSystem.recordRenderCall(capture);
                    }
                    return future;
                }

                @Override
                public List<RcmdRecipeView> recipeData(String itemId) {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.level == null) {
                        return null;
                    }
                    var registries = minecraft.level.registryAccess();
                    var recipes = new ArrayList<RcmdRecipeView>();
                    for (var holder : minecraft.level.getRecipeManager().getOrderedRecipes()) {
                        var recipe = holder.value();
                        var result = recipe.getResultItem(registries);
                        if (result.isEmpty() || !itemId.equals(RcmdRecipeCodec211.itemId(result))) {
                            continue;
                        }
                        recipes.add(RcmdRecipeCodec211.recipeView(holder.id().toString(), recipe, result));
                    }
                    return List.copyOf(recipes);
                }

                @Override
                public RMcpStaringBlockData staringBlockData(boolean includeFluid) {
                    var minecraft = Minecraft.getInstance();
                    var player = minecraft.player;
                    if (minecraft.level == null || player == null) {
                        return null;
                    }
                    var picked = player.pick(256.0D, 0.0F, includeFluid);
                    if (!(picked instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
                        return null;
                    }
                    var pos = hitResult.getBlockPos();
                    var blockState = minecraft.level.getBlockState(pos);
                    var blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                    RMcpFluidData fluid = null;
                    if (includeFluid) {
                        var fluidState = minecraft.level.getFluidState(pos);
                        if (!fluidState.isEmpty()) {
                            var fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType()).toString();
                            fluid = new RMcpFluidData(fluidId, RMcpMcDataCodec211.stateString(fluidId, fluidState.toString()));
                        }
                    }
                    return new RMcpStaringBlockData(
                            minecraft.level.dimension().location().toString(),
                            new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ()),
                            blockId,
                            RMcpMcDataCodec211.stateString(blockId, blockState.toString()),
                            fluid
                    );
                }

                @Override
                public RMcpBlockStateData blockStateData(String dim, int x, int y, int z) {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.level == null) {
                        return null;
                    }
                    if (!minecraft.level.dimension().location().toString().equals(dim)) {
                        return null;
                    }
                    var pos = new BlockPos(x, y, z);
                    var blockState = minecraft.level.getBlockState(pos);
                    var blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                    return new RMcpBlockStateData(
                            dim,
                            new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ()),
                            blockId,
                            RMcpMcDataCodec211.stateString(blockId, blockState.toString())
                    );
                }

                @Override
                public RMcpBlockStateBatchData blockStateBatchData(String dim, List<RMcpBlockPosData> positions) {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.level == null) {
                        return null;
                    }
                    if (!minecraft.level.dimension().location().toString().equals(dim)) {
                        return null;
                    }
                    var blocks = new ArrayList<RMcpBlockStateEntryData>();
                    for (var posData : positions) {
                        var pos = new BlockPos(posData.x(), posData.y(), posData.z());
                        var blockState = minecraft.level.getBlockState(pos);
                        var blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                        blocks.add(new RMcpBlockStateEntryData(
                                new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ()),
                                blockId,
                                RMcpMcDataCodec211.stateString(blockId, blockState.toString())
                        ));
                    }
                    return new RMcpBlockStateBatchData("blockstate-batch-v1", dim, positions.size(), blocks);
                }

                @Override
                public RMcpBlockEntityData blockEntityData(String dim, int x, int y, int z) {
                    return RMcpClientBridge.requestBlockEntity(dim, x, y, z);
                }

                @Override
                public RMcpHarvestToolData harvestToolData(String blockId, String dim, Integer x, Integer y, Integer z) {
                    return RMcpClientBridge.requestHarvestTool(blockId, dim, x, y, z);
                }

                @Override
                public RMcpContainerData containerData(String pos, String side) {
                    return RMcpClientBridge.requestContainer(pos, side);
                }

                @Override
                public RMcpContainerMoveData moveContainerItems(String fromPos, String fromSide, int fromSlot, String toPos, String toSide, Integer toSlot, int count, boolean dryRun) {
                    return RMcpClientBridge.requestContainerMove(fromPos, fromSide, fromSlot, toPos, toSide, toSlot, count, dryRun);
                }

                @Override
                public RMcpBlockActionData placeBlock(int x, int y, int z) {
                    return RMcpClientBridge.requestPlaceBlock(x, y, z);
                }

                @Override
                public RMcpBlockActionData breakBlock(int x, int y, int z) {
                    return RMcpClientBridge.requestBreakBlock(x, y, z);
                }

                @Override
                public RMcpBlockBatchActionData placeBlocks(List<RMcpBlockPosData> positions) {
                    return RMcpClientBridge.requestPlaceBlocks(positions);
                }

                @Override
                public RMcpBlockBatchActionData breakBlocks(List<RMcpBlockPosData> positions) {
                    return RMcpClientBridge.requestBreakBlocks(positions);
                }

                @Override
                public RMcpBlockBatchActionData placeBlockBox(RMcpBlockPosData from, RMcpBlockPosData to) {
                    return RMcpClientBridge.requestPlaceBlockBox(from, to);
                }

                @Override
                public RMcpBlockBatchActionData breakBlockBox(RMcpBlockPosData from, RMcpBlockPosData to) {
                    return RMcpClientBridge.requestBreakBlockBox(from, to);
                }

                @Override
                public RMcpChunkSemanticData chunkData(int chunkX, int chunkZ) {
                    return chunkSemanticData(Minecraft.getInstance(), chunkX, chunkZ);
                }

                @Override
                public RMcpSectionSemanticData sectionData(int chunkX, int sectionY, int chunkZ) {
                    return sectionSemanticData(Minecraft.getInstance(), chunkX, sectionY, chunkZ);
                }

                @Override
                public RMcpNearbyResourcesData nearbyResourcesData(String dim, int x, int y, int z, int chunkRadius, int sectionRadius) {
                    return RDIMain.nearbyResourcesData(Minecraft.getInstance(), dim, x, y, z, chunkRadius, sectionRadius);
                }

                @Override
                public RMcpNearbyEntitiesData nearbyEntitiesData(String dim, int x, int y, int z, double radius, List<String> categories, int limit) {
                    return RDIMain.nearbyEntitiesData(Minecraft.getInstance(), dim, x, y, z, radius, categories, limit);
                }

                @Override
                public RMcpEntityData staringEntityData() {
                    var minecraft = Minecraft.getInstance();
                    var player = minecraft.player;
                    if (minecraft.level == null || player == null) {
                        return null;
                    }
                    double range = 64.0D;
                    var eye = player.getEyePosition(0.0F);
                    var view = player.getViewVector(0.0F);
                    var end = eye.add(view.scale(range));
                    var blockHit = player.pick(range, 0.0F, false);
                    var maxDistanceSqr = blockHit.getType() == HitResult.Type.MISS ? range * range : eye.distanceToSqr(blockHit.getLocation());
                    var box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0D);
                    var hit = ProjectileUtil.getEntityHitResult(
                            player,
                            eye,
                            end,
                            box,
                            entity -> !entity.isSpectator() && entity.isPickable(),
                            maxDistanceSqr
                    );
                    if (hit == null) {
                        return null;
                    }
                    var entity = hit.getEntity();
                    var dim = minecraft.level.dimension().location().toString();
                    var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                    return new RMcpEntityData(
                            dim,
                            entity.getStringUUID(),
                            type,
                            entity.getName().getString(),
                            new RMcpPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()),
                            Math.sqrt(player.distanceToSqr(entity))
                    );
                }

                @Override
                public RMcpEntityDetailData entityData(UUID uuid) {
                    var minecraft = Minecraft.getInstance();
                    if (minecraft.level == null) {
                        return null;
                    }
                    for (var entity : minecraft.level.entitiesForRendering()) {
                        if (entity.getUUID().equals(uuid)) {
                            return RMcpMcDataCodec211.entityDetail(minecraft.level.dimension().location().toString(), entity, minecraft.level.registryAccess());
                        }
                    }
                    return null;
                }

                @Override
                public RMcpLangKeyIndex langKeyIndex() {
                    return RDIMain.langKeyIndex(Minecraft.getInstance());
                }

                @Override
                public RMcpPlayerData playerData(UUID uuid) {
                    var minecraft = Minecraft.getInstance();
                    var player = findPlayer(minecraft, uuid);
                    if (minecraft.level == null || player == null) {
                        return null;
                    }
                    return playerBrief(minecraft, player);
                }

                @Override
                public RMcpPlayerDetailData playerDetailData(UUID uuid) {
                    var minecraft = Minecraft.getInstance();
                    var player = findPlayer(minecraft, uuid);
                    if (minecraft.level == null || player == null) {
                        return null;
                    }
                    var dim = minecraft.level.dimension().location().toString();
                    return new RMcpPlayerDetailData(
                            playerBrief(minecraft, player),
                            RMcpMcDataCodec211.entityDetail(dim, player, minecraft.level.registryAccess()),
                            profileMap(player.getGameProfile()),
                            playerMap(minecraft, player),
                            inventoryMap(player, minecraft.level.registryAccess())
                    );
                }
            });
        } catch (Exception e) {
            LGR.error("RDI MCP HTTP server启动失败", e);
        }
    }

    public static Button JOIN_BUTTON = Button.builder(Component.literal("进入地图 · " + HOST_NAME),(btn)->{
        HostAndPort hp = HostAndPort.fromString(GAME_IP);
        ConnectScreen.startConnecting(
                new TitleScreen(),
                Minecraft.getInstance(),
                new ServerAddress(hp.getHost(), hp.getPort()),
                new ServerData("rdi", GAME_IP, ServerData.Type.OTHER),
                false,
                null
        );
    }).bounds(100,0,200,50).build();

    public static void layoutJoinButton(int screenWidth) {
        JOIN_BUTTON.setX(screenWidth / 2 - 100);
        JOIN_BUTTON.setY(0);
        JOIN_BUTTON.setWidth(200);
        JOIN_BUTTON.setHeight(20);
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("rdi")
                        .then(Commands.literal("firmchunk")
                                .then(Commands.literal("show").executes(context -> {
                                    RDI.SHOW_FIRM_CHUNKS = true;
                                    context.getSource().sendSuccess(() -> Component.literal("永久区块边框：显示"), false);
                                    return 1;
                                }))
                                .then(Commands.literal("hide").executes(context -> {
                                    RDI.SHOW_FIRM_CHUNKS = false;
                                    context.getSource().sendSuccess(() -> Component.literal("永久区块边框：隐藏"), false);
                                    return 1;
                                }))
                        )
        );
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (!RcmdClientCommands.isRcmd(message)) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var result = RcmdClientCommands.dispatch(minecraft, message);
        if (!result.found()) {
            return;
        }
        event.setCanceled(true);
        RcmdClientCommands.reply(minecraft, result.result());
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !RDI.SHOW_FIRM_CHUNKS) {
            return;
        }
        var cameraEntity = event.getCamera().getEntity();
        if (cameraEntity == null) {
            return;
        }
        SectionPos sectionPos = SectionPos.of(cameraEntity);
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType renderType = RenderType.debugLineStrip(4.0);
        var vertexConsumer = bufferSource.getBuffer(renderType);
        Matrix4f matrix4f = event.getPoseStack().last().pose();
        float minX = (float) (sectionPos.minBlockX() - cameraPos.x);
        float minY = (float) (sectionPos.minBlockY() - cameraPos.y);
        float minZ = (float) (sectionPos.minBlockZ() - cameraPos.z);
        float maxX = minX + 16.0F;
        float maxY = minY + 16.0F;
        float maxZ = minZ + 16.0F;

        addLine(vertexConsumer, matrix4f, minX, minY, minZ, maxX, minY, minZ);
        addLine(vertexConsumer, matrix4f, maxX, minY, minZ, maxX, minY, maxZ);
        addLine(vertexConsumer, matrix4f, maxX, minY, maxZ, minX, minY, maxZ);
        addLine(vertexConsumer, matrix4f, minX, minY, maxZ, minX, minY, minZ);

        addLine(vertexConsumer, matrix4f, minX, maxY, minZ, maxX, maxY, minZ);
        addLine(vertexConsumer, matrix4f, maxX, maxY, minZ, maxX, maxY, maxZ);
        addLine(vertexConsumer, matrix4f, maxX, maxY, maxZ, minX, maxY, maxZ);
        addLine(vertexConsumer, matrix4f, minX, maxY, maxZ, minX, maxY, minZ);

        addLine(vertexConsumer, matrix4f, minX, minY, minZ, minX, maxY, minZ);
        addLine(vertexConsumer, matrix4f, maxX, minY, minZ, maxX, maxY, minZ);
        addLine(vertexConsumer, matrix4f, maxX, minY, maxZ, maxX, maxY, maxZ);
        addLine(vertexConsumer, matrix4f, minX, minY, maxZ, minX, maxY, maxZ);

        bufferSource.endBatch(renderType);
    }

    private static void addLine(VertexConsumer vertexConsumer, Matrix4f matrix4f, float x1, float y1, float z1, float x2, float y2, float z2) {
        vertexConsumer.addVertex(matrix4f, x1, y1, z1).setColor(1.0F, 1.0F, 0.0F, 0.0F);
        vertexConsumer.addVertex(matrix4f, x1, y1, z1).setColor(1.0F, 1.0F, 0.0F, 1.0F);
        vertexConsumer.addVertex(matrix4f, x2, y2, z2).setColor(1.0F, 1.0F, 0.0F, 1.0F);
        vertexConsumer.addVertex(matrix4f, x2, y2, z2).setColor(1.0F, 1.0F, 0.0F, 0.0F);
    }

    private static Player findPlayer(Minecraft minecraft, UUID uuid) {
        if (uuid == null) {
            return minecraft.player;
        }
        if (minecraft.level == null) {
            return null;
        }
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Player player && player.getUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private static RMcpPlayerData playerBrief(Minecraft minecraft, Player player) {
        var dim = player.level().dimension().location().toString();
        var playerInfo = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(player.getUUID());
        return new RMcpPlayerData(
                dim,
                player.getStringUUID(),
                player.getName().getString(),
                new RMcpPosData(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()),
                player.getHealth(),
                player.getMaxHealth(),
                player.getFoodData().getFoodLevel(),
                playerInfo == null || playerInfo.getGameMode() == null ? null : playerInfo.getGameMode().getName(),
                playerInfo == null ? null : playerInfo.getLatency()
        );
    }

    private static RMcpInventoryData inventoryData(Minecraft minecraft) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            return null;
        }
        var inventory = player.getInventory();
        var registryAccess = level.registryAccess();
        var dim = level.dimension().location().toString();
        var hotbar = compactItemRange("hotbar", inventory.items, 0, 9, registryAccess);
        var items = compactItemRange("inventory", inventory.items, 9, inventory.items.size(), registryAccess);
        var armor = compactItemRange("armor", inventory.armor, 0, inventory.armor.size(), registryAccess);
        var offhand = compactItemRange("offhand", inventory.offhand, 0, inventory.offhand.size(), registryAccess);
        return new RMcpInventoryData(
                INVENTORY_FORMAT,
                dim,
                inventory.selected,
                compactItem("hotbar", inventory.selected, inventory.selected, inventory.getSelected(), registryAccess),
                hotbar,
                items,
                armor,
                offhand,
                inventorySummary(allInventoryStacks(player))
        );
    }

    private static RMcpInventorySwapData swapInventorySlots(Minecraft minecraft, String fromText, String toText, boolean dryRun) {
        try {
            return minecraft.submit(() -> swapInventorySlotsOnClient(minecraft, fromText, toText, dryRun)).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RMcpEndpointException(RErrorCode.ACTION_FAILED);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RMcpEndpointException endpointException) {
                throw endpointException;
            }
            throw new RMcpEndpointException(RErrorCode.ACTION_FAILED);
        } catch (TimeoutException e) {
            throw new RMcpEndpointException(RErrorCode.ACTION_FAILED);
        }
    }

    private static RMcpInventoryMoveData moveInventoryItems(Minecraft minecraft, String fromText, String toText, int count, boolean dryRun) {
        try {
            return minecraft.submit(() -> moveInventoryItemsOnClient(minecraft, fromText, toText, count, dryRun)).get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RMcpEndpointException(RErrorCode.ACTION_FAILED);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RMcpEndpointException endpointException) {
                throw endpointException;
            }
            throw new RMcpEndpointException(RErrorCode.ACTION_FAILED);
        } catch (TimeoutException e) {
            throw new RMcpEndpointException(RErrorCode.ACTION_FAILED);
        }
    }

    private static RMcpInventorySwapData swapInventorySlotsOnClient(Minecraft minecraft, String fromText, String toText, boolean dryRun) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || minecraft.gameMode == null) {
            throw new RMcpEndpointException(RErrorCode.NO_PLAYER);
        }
        if (player.containerMenu != player.inventoryMenu) {
            throw new RMcpEndpointException(RErrorCode.BUSY_CONTAINER_OPEN);
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            throw new RMcpEndpointException(RErrorCode.CARRIED_ITEM_NOT_EMPTY);
        }
        var from = parseInventorySlot(fromText);
        var to = parseInventorySlot(toText);
        if (from.canonical().equals(to.canonical())) {
            throw new RMcpEndpointException(RErrorCode.SAME_SLOT);
        }
        var registryAccess = level.registryAccess();
        var fromStack = stackAtSlot(player, from);
        var toStack = stackAtSlot(player, to);
        var beforeFrom = itemAtSlot(player, from, registryAccess);
        var beforeTo = itemAtSlot(player, to, registryAccess);
        if (!dryRun && !fromStack.isEmpty() && !toStack.isEmpty() && fromStack.isStackable() && toStack.isStackable() && ItemStack.isSameItemSameComponents(fromStack, toStack)) {
            throw new RMcpEndpointException(RErrorCode.UNSUPPORTED_MERGE_RISK);
        }
        if (!dryRun) {
            var menu = player.inventoryMenu;
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, from.menuSlot(), 0, ClickType.PICKUP, player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, to.menuSlot(), 0, ClickType.PICKUP, player);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, from.menuSlot(), 0, ClickType.PICKUP, player);
        }
        var afterFrom = itemAtSlot(player, from, registryAccess);
        var afterTo = itemAtSlot(player, to, registryAccess);
        return new RMcpInventorySwapData(
                INVENTORY_SWAP_FORMAT,
                from.canonical(),
                to.canonical(),
                dryRun,
                !itemsEqual(beforeFrom, afterFrom) || !itemsEqual(beforeTo, afterTo),
                beforeFrom,
                beforeTo,
                afterFrom,
                afterTo,
                inventoryData(minecraft)
        );
    }

    private static RMcpInventoryMoveData moveInventoryItemsOnClient(Minecraft minecraft, String fromText, String toText, int count, boolean dryRun) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || minecraft.gameMode == null) {
            return null;
        }
        if (player.containerMenu != player.inventoryMenu) {
            throw new RMcpEndpointException(RErrorCode.BUSY_CONTAINER_OPEN);
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            throw new RMcpEndpointException(RErrorCode.CARRIED_ITEM_NOT_EMPTY);
        }
        if (count <= 0) {
            throw new RMcpEndpointException(RErrorCode.BAD_COUNT);
        }
        var from = parseInventorySlot(fromText);
        var to = parseInventorySlot(toText);
        if (from.canonical().equals(to.canonical())) {
            throw new RMcpEndpointException(RErrorCode.SAME_SLOT);
        }
        var registryAccess = level.registryAccess();
        var fromStack = stackAtSlot(player, from);
        var toStack = stackAtSlot(player, to);
        var beforeFrom = itemAtSlot(player, from, registryAccess);
        var beforeTo = itemAtSlot(player, to, registryAccess);
        if (fromStack.isEmpty()) {
            throw new RMcpEndpointException(RErrorCode.EMPTY_SOURCE);
        }
        if (count > fromStack.getCount()) {
            throw new RMcpEndpointException(RErrorCode.BAD_COUNT);
        }
        if (!toStack.isEmpty() && !ItemStack.isSameItemSameComponents(fromStack, toStack)) {
            throw new RMcpEndpointException(RErrorCode.INCOMPATIBLE_TARGET);
        }
        var sourceSlot = player.inventoryMenu.getSlot(from.menuSlot());
        if (!sourceSlot.mayPickup(player)) {
            throw new RMcpEndpointException(RErrorCode.EMPTY_SOURCE);
        }
        var targetSlot = player.inventoryMenu.getSlot(to.menuSlot());
        if (!targetSlot.mayPlace(fromStack)) {
            throw new RMcpEndpointException(RErrorCode.INCOMPATIBLE_TARGET);
        }
        int targetCount = toStack.isEmpty() ? 0 : toStack.getCount();
        int targetCapacity = targetSlot.getMaxStackSize(fromStack) - targetCount;
        if (count > targetCapacity) {
            throw new RMcpEndpointException(RErrorCode.TARGET_FULL);
        }
        if (!dryRun) {
            clickInventorySlot(minecraft, player, from, 0);
            if (count == fromStack.getCount()) {
                clickInventorySlot(minecraft, player, to, 0);
            } else {
                for (int i = 0; i < count; i++) {
                    clickInventorySlot(minecraft, player, to, 1);
                }
                clickInventorySlot(minecraft, player, from, 0);
            }
            if (!player.inventoryMenu.getCarried().isEmpty()) {
                clickInventorySlot(minecraft, player, from, 0);
            }
            if (!player.inventoryMenu.getCarried().isEmpty()) {
                throw new RMcpEndpointException(RErrorCode.CARRIED_ITEM_NOT_EMPTY);
            }
        }
        var afterFrom = itemAtSlot(player, from, registryAccess);
        var afterTo = itemAtSlot(player, to, registryAccess);
        return new RMcpInventoryMoveData(
                INVENTORY_MOVE_FORMAT,
                from.canonical(),
                to.canonical(),
                count,
                dryRun,
                !itemsEqual(beforeFrom, afterFrom) || !itemsEqual(beforeTo, afterTo),
                dryRun ? 0 : count,
                beforeFrom,
                beforeTo,
                afterFrom,
                afterTo,
                inventoryData(minecraft)
        );
    }

    private static void clickInventorySlot(Minecraft minecraft, Player player, InventorySlotRef slot, int button) {
        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot.menuSlot(), button, ClickType.PICKUP, player);
    }

    private static RMcpSituationData situationData(Minecraft minecraft, double entityRadius, int resourceChunkRadius, int resourceSectionRadius) {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null) {
            return null;
        }
        var dim = level.dimension().location().toString();
        var pos = player.blockPosition();
        var inventory = inventoryData(minecraft);
        var nearbyEntities = nearbyEntitiesData(minecraft, dim, pos.getX(), pos.getY(), pos.getZ(), entityRadius, List.of("monster", "animal"), 12);
        var nearbyResources = nearbyResourcesData(minecraft, dim, pos.getX(), pos.getY(), pos.getZ(), resourceChunkRadius, resourceSectionRadius);
        return new RMcpSituationData(
                SITUATION_FORMAT,
                playerBrief(minecraft, player),
                environmentData(level, player),
                new RMcpSituationData.Inventory(
                        inventory.selectedHotbarSlot(),
                        inventory.selectedItem(),
                        inventory.armor(),
                        inventory.offhand().isEmpty() ? null : inventory.offhand().get(0),
                        inventory.summary()
                ),
                new RMcpSituationData.Nearby(
                        new RMcpSituationData.Range(entityRadius, resourceChunkRadius, resourceSectionRadius),
                        nearbyEntities.summary(),
                        nearbyEntities.entities(),
                        nearbyResources.features(),
                        nearbyResources.resources().stream().limit(12).toList()
                )
        );
    }

    private static RMcpSituationData.Environment environmentData(net.minecraft.client.multiplayer.ClientLevel level, Player player) {
        var pos = player.blockPosition();
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        return new RMcpSituationData.Environment(
                level.dimension().location().toString(),
                biomeId(level, pos),
                level.getGameTime(),
                level.getDayTime(),
                timeOfDay,
                timeBucket(timeOfDay),
                level.isRaining(),
                level.isThundering(),
                level.getDifficulty().getKey(),
                new RMcpSituationData.Light(
                        level.getBrightness(LightLayer.BLOCK, pos),
                        level.getBrightness(LightLayer.SKY, pos),
                        level.getMaxLocalRawBrightness(pos)
                ),
                level.canSeeSky(pos),
                player.isInWater(),
                player.isUnderWater(),
                player.onGround()
        );
    }

    private static String biomeId(net.minecraft.client.multiplayer.ClientLevel level, BlockPos pos) {
        return level.getBiome(pos)
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("unknown");
    }

    private static String timeBucket(long timeOfDay) {
        if (timeOfDay < 1000 || timeOfDay >= 23000) {
            return "dawn";
        }
        if (timeOfDay < 12000) {
            return "day";
        }
        if (timeOfDay < 13800) {
            return "dusk";
        }
        return "night";
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

    private static InventorySlotRef parseInventorySlot(String text) {
        var parts = text == null ? new String[0] : text.trim().toLowerCase().split(":", 2);
        if (parts.length != 2 || parts[0].isBlank()) {
            throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
        }
        int slot;
        try {
            slot = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
        }
        return switch (parts[0].trim()) {
            case "inventory" -> inventorySlot(slot);
            case "hotbar" -> {
                if (slot < 0 || slot > 8) {
                    throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
                }
                yield inventorySlot(slot);
            }
            case "main" -> {
                if (slot < 0 || slot > 26) {
                    throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
                }
                yield inventorySlot(slot + 9);
            }
            case "armor" -> armorSlot(slot);
            case "offhand" -> offhandSlot(slot);
            default -> throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
        };
    }

    private static InventorySlotRef inventorySlot(int slot) {
        if (slot < 0 || slot > 35) {
            throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
        }
        int menuSlot = slot < 9 ? slot + 36 : slot;
        return new InventorySlotRef("inventory", slot, "inventory:" + slot, menuSlot);
    }

    private static InventorySlotRef armorSlot(int slot) {
        if (slot < 0 || slot > 3) {
            throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
        }
        return new InventorySlotRef("armor", slot, "armor:" + slot, 8 - slot);
    }

    private static InventorySlotRef offhandSlot(int slot) {
        if (slot != 0) {
            throw new RMcpEndpointException(RErrorCode.BAD_SLOT);
        }
        return new InventorySlotRef("offhand", 0, "offhand:0", 45);
    }

    private static RMcpInventoryData.Item itemAtSlot(Player player, InventorySlotRef slot, HolderLookup.Provider registryAccess) {
        var stack = switch (slot.section()) {
            case "inventory" -> player.getInventory().items.get(slot.index());
            case "armor" -> player.getInventory().armor.get(slot.index());
            case "offhand" -> player.getInventory().offhand.get(slot.index());
            default -> ItemStack.EMPTY;
        };
        return compactItem(slot.section(), slot.index(), slot.index() < 9 && "inventory".equals(slot.section()) ? slot.index() : null, stack, registryAccess);
    }

    private static ItemStack stackAtSlot(Player player, InventorySlotRef slot) {
        return switch (slot.section()) {
            case "inventory" -> player.getInventory().items.get(slot.index());
            case "armor" -> player.getInventory().armor.get(slot.index());
            case "offhand" -> player.getInventory().offhand.get(slot.index());
            default -> ItemStack.EMPTY;
        };
    }

    private static boolean itemsEqual(RMcpInventoryData.Item left, RMcpInventoryData.Item right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.id().equals(right.id())
                && left.count() == right.count()
                && left.snbt().equals(right.snbt());
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

    private static List<ItemStack> allInventoryStacks(Player player) {
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

    private static RMcpNearbyEntitiesData nearbyEntitiesData(Minecraft minecraft, String dim, int x, int y, int z, double radius, List<String> categories, int limit) {
        var level = minecraft.level;
        if (level == null) {
            return null;
        }
        if (!level.dimension().location().toString().equals(dim)) {
            throw new RMcpEndpointException(RErrorCode.DIM_NOT_LOADED);
        }
        var center = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        double radiusSqr = radius * radius;
        var entities = new ArrayList<RMcpNearbyEntitiesData.Entity>();
        for (var entity : level.entitiesForRendering()) {
            if (entity == minecraft.player || entity.isRemoved()) {
                continue;
            }
            var category = entityCategory(entity);
            if (!matchesCategory(category, categories)) {
                continue;
            }
            double distanceSqr = entity.position().distanceToSqr(center);
            if (distanceSqr > radiusSqr) {
                continue;
            }
            entities.add(nearbyEntityData(dim, entity, category, Math.sqrt(distanceSqr)));
        }
        var sortedEntities = entities.stream()
                .sorted(Comparator.comparingDouble(RMcpNearbyEntitiesData.Entity::distance)
                        .thenComparing(RMcpNearbyEntitiesData.Entity::type)
                        .thenComparing(RMcpNearbyEntitiesData.Entity::uuid))
                .toList();
        var returnedEntities = sortedEntities.stream()
                .limit(limit)
                .toList();

        int monsters = 0;
        int animals = 0;
        RMcpNearbyEntitiesData.EntityRef nearestMonster = null;
        RMcpNearbyEntitiesData.EntityRef nearestAnimal = null;
        for (var entity : sortedEntities) {
            if ("monster".equals(entity.category())) {
                monsters++;
                if (nearestMonster == null) {
                    nearestMonster = new RMcpNearbyEntitiesData.EntityRef(entity.type(), entity.distance());
                }
            } else if ("animal".equals(entity.category())) {
                animals++;
                if (nearestAnimal == null) {
                    nearestAnimal = new RMcpNearbyEntitiesData.EntityRef(entity.type(), entity.distance());
                }
            }
        }

        return new RMcpNearbyEntitiesData(
                NEARBY_ENTITIES_FORMAT,
                dim,
                new RMcpNearbyEntitiesData.Center(
                        new RMcpBlockPosData(x, y, z),
                        Math.floorDiv(x, 16),
                        Math.floorDiv(z, 16),
                        Math.floorDiv(y, 16)
                ),
                radius,
                new RMcpNearbyEntitiesData.Summary(sortedEntities.size(), monsters, animals, nearestMonster, nearestAnimal),
                returnedEntities
        );
    }

    private static RMcpNearbyEntitiesData.Entity nearbyEntityData(String dim, Entity entity, String category, double distance) {
        var type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        Float health = null;
        Float maxHealth = null;
        Boolean baby = null;
        if (entity instanceof LivingEntity livingEntity) {
            health = livingEntity.getHealth();
            maxHealth = livingEntity.getMaxHealth();
            baby = livingEntity.isBaby();
        }
        return new RMcpNearbyEntitiesData.Entity(
                dim,
                entity.getStringUUID(),
                type,
                entity.getName().getString(),
                category,
                new RMcpPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()),
                distance,
                health,
                maxHealth,
                "monster".equals(category),
                baby
        );
    }

    private static String entityCategory(Entity entity) {
        var mobCategory = entity.getType().getCategory();
        if (mobCategory == MobCategory.MONSTER || entity instanceof Monster) {
            return "monster";
        }
        if (entity instanceof Animal
                || mobCategory == MobCategory.CREATURE
                || mobCategory == MobCategory.AMBIENT
                || mobCategory == MobCategory.AXOLOTLS
                || mobCategory == MobCategory.UNDERGROUND_WATER_CREATURE
                || mobCategory == MobCategory.WATER_CREATURE
                || mobCategory == MobCategory.WATER_AMBIENT) {
            return "animal";
        }
        return mobCategory.getName();
    }

    private static boolean matchesCategory(String category, List<String> categories) {
        return categories.contains("all") || categories.contains(category);
    }

    private static RMcpChunkSemanticData chunkSemanticData(Minecraft minecraft, int chunkX, int chunkZ) {
        var level = minecraft.level;
        if (level == null) {
            return null;
        }
        var chunk = loadedChunk(level, chunkX, chunkZ);
        var sections = new ArrayList<RMcpChunkSemanticData.Section>();
        var chunkCounts = new LinkedHashMap<String, Integer>();
        int nonEmptySections = 0;
        int nonAir = 0;
        Integer minNonAirY = null;
        Integer maxNonAirY = null;
        for (int sectionY = level.getMinSection(); sectionY < level.getMaxSection(); sectionY++) {
            var analysis = analyzeSection(chunk, sectionY, false);
            var summary = analysis.summary();
            if (!summary.empty()) {
                nonEmptySections++;
            }
            nonAir += summary.nonAir();
            for (var entry : analysis.counts().entrySet()) {
                chunkCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            if (analysis.minNonAirY() != null) {
                minNonAirY = minNonAirY == null ? analysis.minNonAirY() : Math.min(minNonAirY, analysis.minNonAirY());
                maxNonAirY = maxNonAirY == null ? analysis.maxNonAirY() : Math.max(maxNonAirY, analysis.maxNonAirY());
            }
            sections.add(new RMcpChunkSemanticData.Section(
                    sectionY,
                    blockYRange(sectionY),
                    summary.empty(),
                    summary.nonAir(),
                    summary.topBlocks(),
                    "/section?x=" + chunkX + "&y=" + sectionY + "&z=" + chunkZ
            ));
        }
        var heightRange = minNonAirY == null ? null : new RMcpSectionSemanticData.BlockYRange(minNonAirY, maxNonAirY);
        return new RMcpChunkSemanticData(
                CHUNK_FORMAT,
                level.dimension().location().toString(),
                new RMcpSectionSemanticData.ChunkPos(chunkX, chunkZ),
                new RMcpChunkSemanticData.Summary(
                        level.getSectionsCount(),
                        nonEmptySections,
                        nonAir,
                        topBlocks(chunkCounts, 16),
                        heightRange
                ),
                List.copyOf(sections)
        );
    }

    private static RMcpSectionSemanticData sectionSemanticData(Minecraft minecraft, int chunkX, int sectionY, int chunkZ) {
        var level = minecraft.level;
        if (level == null) {
            return null;
        }
        var chunk = loadedChunk(level, chunkX, chunkZ);
        if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) {
            throw new RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE);
        }
        var analysis = analyzeSection(chunk, sectionY, true);
        return new RMcpSectionSemanticData(
                SECTION_FORMAT,
                level.dimension().location().toString(),
                new RMcpSectionSemanticData.ChunkPos(chunkX, chunkZ),
                sectionY,
                blockYRange(sectionY),
                analysis.summary(),
                analysis.layers(),
                analysis.legend(),
                analysis.features()
        );
    }

    private static RMcpNearbyResourcesData nearbyResourcesData(Minecraft minecraft, String dim, int x, int y, int z, int chunkRadius, int sectionRadius) {
        var level = minecraft.level;
        if (level == null) {
            return null;
        }
        if (!level.dimension().location().toString().equals(dim)) {
            throw new RMcpEndpointException(RErrorCode.DIM_NOT_LOADED);
        }
        int centerChunkX = Math.floorDiv(x, 16);
        int centerChunkZ = Math.floorDiv(z, 16);
        int centerSectionY = Math.floorDiv(y, 16);
        int minSectionY = Math.max(level.getMinSection(), centerSectionY - sectionRadius);
        int maxSectionY = Math.min(level.getMaxSection() - 1, centerSectionY + sectionRadius);
        if (minSectionY > maxSectionY) {
            throw new RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE);
        }

        var resources = new LinkedHashMap<String, ResourceAccumulator>();
        var topCounts = new LinkedHashMap<String, Integer>();
        int loadedChunks = 0;
        int skippedChunks = 0;
        int scannedSections = 0;
        int scannedBlocks = 0;
        boolean hasWater = false;
        boolean hasLava = false;
        boolean hasOre = false;
        boolean hasWood = false;
        boolean hasCrops = false;
        boolean hasBlockEntities = false;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                var chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    skippedChunks++;
                    continue;
                }
                loadedChunks++;
                var blockEntities = chunk.getBlockEntities();
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
                    scannedSections++;
                    for (int localY = 0; localY < 16; localY++) {
                        int blockY = (sectionY << 4) + localY;
                        for (int localZ = 0; localZ < 16; localZ++) {
                            int blockZ = chunk.getPos().getMinBlockZ() + localZ;
                            for (int localX = 0; localX < 16; localX++) {
                                int blockX = chunk.getPos().getMinBlockX() + localX;
                                scannedBlocks++;
                                var state = section.getBlockState(localX, localY, localZ);
                                var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                                topCounts.merge(blockId, 1, Integer::sum);

                                var fluidState = state.getFluidState();
                                if (!fluidState.isEmpty()) {
                                    var fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType()).toString();
                                    var fluidCategory = fluidResourceCategory(fluidId);
                                    hasWater = hasWater || "water".equals(fluidCategory);
                                    hasLava = hasLava || "lava".equals(fluidCategory);
                                    addResource(resources, fluidId, fluidCategory, chunkX, chunkZ, sectionY, blockX, blockY, blockZ, x, y, z);
                                }

                                var category = blockResourceCategory(blockId);
                                if (!blockEntities.isEmpty() && blockEntities.containsKey(new BlockPos(blockX, blockY, blockZ))) {
                                    hasBlockEntities = true;
                                    category = category == null ? "block_entity" : category;
                                }
                                if (category == null) {
                                    continue;
                                }
                                hasOre = hasOre || "ore".equals(category);
                                hasWood = hasWood || "wood".equals(category);
                                hasCrops = hasCrops || "crop".equals(category);
                                addResource(resources, blockId, category, chunkX, chunkZ, sectionY, blockX, blockY, blockZ, x, y, z);
                            }
                        }
                    }
                }
            }
        }

        var resourceList = resources.values().stream()
                .sorted(Comparator.comparingInt(ResourceAccumulator::count).reversed()
                        .thenComparing(ResourceAccumulator::id))
                .limit(64)
                .map(ResourceAccumulator::data)
                .toList();
        return new RMcpNearbyResourcesData(
                NEARBY_RESOURCES_FORMAT,
                dim,
                new RMcpNearbyResourcesData.Center(
                        new RMcpBlockPosData(x, y, z),
                        centerChunkX,
                        centerChunkZ,
                        centerSectionY
                ),
                new RMcpNearbyResourcesData.Range(chunkRadius, sectionRadius),
                new RMcpNearbyResourcesData.Scan(loadedChunks, skippedChunks, scannedSections, scannedBlocks),
                resourceList,
                topBlocks(topCounts, 24),
                new RMcpNearbyResourcesData.Features(hasWater, hasLava, hasOre, hasWood, hasCrops, hasBlockEntities)
        );
    }

    private static String fluidResourceCategory(String fluidId) {
        if ("minecraft:water".equals(fluidId) || fluidId.endsWith(":flowing_water")) {
            return "water";
        }
        if ("minecraft:lava".equals(fluidId) || fluidId.endsWith(":flowing_lava")) {
            return "lava";
        }
        return "fluid";
    }

    private static String blockResourceCategory(String id) {
        if (id.contains("_ore") || id.endsWith(":ancient_debris")) {
            return "ore";
        }
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || id.endsWith("_leaves")) {
            return "wood";
        }
        if (id.contains("crop") || id.endsWith(":wheat") || id.endsWith(":carrots") || id.endsWith(":potatoes") || id.endsWith(":beetroots") || id.endsWith(":melon") || id.endsWith(":pumpkin")) {
            return "crop";
        }
        if (id.contains("chest") || id.endsWith(":barrel") || id.contains("shulker_box")) {
            return "container";
        }
        if (id.endsWith(":spawner")) {
            return "spawner";
        }
        return null;
    }

    private static void addResource(
            Map<String, ResourceAccumulator> resources,
            String id,
            String category,
            int chunkX,
            int chunkZ,
            int sectionY,
            int blockX,
            int blockY,
            int blockZ,
            int centerX,
            int centerY,
            int centerZ
    ) {
        resources.computeIfAbsent(id, ignored -> new ResourceAccumulator(id, category))
                .add(chunkX, chunkZ, sectionY, blockX, blockY, blockZ, centerX, centerY, centerZ);
    }

    private static LevelChunk loadedChunk(net.minecraft.client.multiplayer.ClientLevel level, int chunkX, int chunkZ) {
        var chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            throw new RMcpEndpointException(RErrorCode.CHUNK_NOT_LOADED);
        }
        return chunk;
    }

    private static SectionAnalysis analyzeSection(LevelChunk chunk, int sectionY, boolean includeLayers) {
        var level = chunk.getLevel();
        if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) {
            throw new RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE);
        }
        LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
        var ids = includeLayers ? new String[4096] : null;
        var nonAirMask = new boolean[4096];
        var counts = new LinkedHashMap<String, Integer>();
        var sectionPalette = new LinkedHashMap<String, Boolean>();
        boolean hasFluids = false;
        int nonAir = 0;
        int minX = 16;
        int minY = 16;
        int minZ = 16;
        int maxX = -1;
        int maxY = -1;
        int maxZ = -1;
        for (int localY = 0; localY < 16; localY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int index = sectionIndex(localX, localY, localZ);
                    var state = section.getBlockState(localX, localY, localZ);
                    var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    counts.merge(blockId, 1, Integer::sum);
                    sectionPalette.put(blockId, Boolean.TRUE);
                    if (ids != null) {
                        ids[index] = blockId;
                    }
                    if (!state.getFluidState().isEmpty()) {
                        hasFluids = true;
                    }
                    if (!state.isAir()) {
                        nonAir++;
                        nonAirMask[index] = true;
                        minX = Math.min(minX, localX);
                        minY = Math.min(minY, localY);
                        minZ = Math.min(minZ, localZ);
                        maxX = Math.max(maxX, localX);
                        maxY = Math.max(maxY, localY);
                        maxZ = Math.max(maxZ, localZ);
                    }
                }
            }
        }
        var topBlocks = topBlocks(counts, 16);
        var bbox = nonAir == 0 ? null : new RMcpSectionSemanticData.NonAirBox(
                new RMcpBlockPosData(chunk.getPos().getMinBlockX() + minX, (sectionY << 4) + minY, chunk.getPos().getMinBlockZ() + minZ),
                new RMcpBlockPosData(chunk.getPos().getMinBlockX() + maxX, (sectionY << 4) + maxY, chunk.getPos().getMinBlockZ() + maxZ)
        );
        var summary = new RMcpSectionSemanticData.Summary(
                nonAir == 0,
                nonAir,
                sectionPalette.size(),
                topBlocks,
                bbox
        );
        var features = new RMcpSectionSemanticData.Features(
                hasFluids,
                hasBlockEntitiesInSection(chunk, sectionY),
                countRegions(nonAirMask, true),
                countRegions(nonAirMask, false)
        );
        var legend = includeLayers ? legend(counts) : Map.<String, String>of();
        var layers = includeLayers ? layers(ids, legend) : List.<RMcpSectionSemanticData.Layer>of();
        return new SectionAnalysis(summary, layers, legend, features, Map.copyOf(counts), nonAir == 0 ? null : (sectionY << 4) + minY, nonAir == 0 ? null : (sectionY << 4) + maxY);
    }

    private static RMcpSectionSemanticData.BlockYRange blockYRange(int sectionY) {
        int min = sectionY << 4;
        return new RMcpSectionSemanticData.BlockYRange(min, min + 15);
    }

    private static int sectionIndex(int localX, int localY, int localZ) {
        return (localY << 8) | (localZ << 4) | localX;
    }

    private static List<RMcpSectionSemanticData.BlockCount> topBlocks(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> new RMcpSectionSemanticData.BlockCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Map<String, String> legend(Map<String, Integer> counts) {
        var legend = new LinkedHashMap<String, String>();
        legend.put(".", "minecraft:air");
        int symbolIndex = 0;
        boolean hasOther = false;
        for (var block : topBlocks(counts, counts.size())) {
            if ("minecraft:air".equals(block.id())) {
                continue;
            }
            if (symbolIndex >= GRID_SYMBOLS.length()) {
                hasOther = true;
                continue;
            }
            legend.put(String.valueOf(GRID_SYMBOLS.charAt(symbolIndex++)), block.id());
        }
        if (hasOther) {
            legend.put("?", "other");
        }
        return legend;
    }

    private static List<RMcpSectionSemanticData.Layer> layers(String[] ids, Map<String, String> legend) {
        var symbolById = new LinkedHashMap<String, String>();
        for (var entry : legend.entrySet()) {
            symbolById.put(entry.getValue(), entry.getKey());
        }
        var layers = new ArrayList<RMcpSectionSemanticData.Layer>();
        for (int localY = 0; localY < 16; localY++) {
            var grid = new ArrayList<String>();
            var layerCounts = new LinkedHashMap<String, Integer>();
            for (int localZ = 0; localZ < 16; localZ++) {
                var line = new StringBuilder(16);
                for (int localX = 0; localX < 16; localX++) {
                    var id = ids[sectionIndex(localX, localY, localZ)];
                    layerCounts.merge(id, 1, Integer::sum);
                    line.append(symbolById.getOrDefault(id, "?"));
                }
                grid.add(line.toString());
            }
            layers.add(new RMcpSectionSemanticData.Layer(localY, grid, topBlocks(layerCounts, 6)));
        }
        return List.copyOf(layers);
    }

    private static boolean hasBlockEntitiesInSection(LevelChunk chunk, int sectionY) {
        int minY = sectionY << 4;
        int maxY = minY + 15;
        for (var pos : chunk.getBlockEntities().keySet()) {
            if (pos.getY() >= minY && pos.getY() <= maxY) {
                return true;
            }
        }
        return false;
    }

    private static int countRegions(boolean[] nonAirMask, boolean target) {
        var visited = new boolean[4096];
        int regions = 0;
        for (int index = 0; index < nonAirMask.length; index++) {
            if (visited[index] || nonAirMask[index] != target) {
                continue;
            }
            regions++;
            floodRegion(nonAirMask, visited, index, target);
        }
        return regions;
    }

    private static void floodRegion(boolean[] nonAirMask, boolean[] visited, int start, boolean target) {
        var queue = new ArrayDeque<Integer>();
        queue.add(start);
        visited[start] = true;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int localX = index & 15;
            int localZ = (index >> 4) & 15;
            int localY = (index >> 8) & 15;
            addRegionNeighbor(nonAirMask, visited, queue, localX - 1, localY, localZ, target);
            addRegionNeighbor(nonAirMask, visited, queue, localX + 1, localY, localZ, target);
            addRegionNeighbor(nonAirMask, visited, queue, localX, localY - 1, localZ, target);
            addRegionNeighbor(nonAirMask, visited, queue, localX, localY + 1, localZ, target);
            addRegionNeighbor(nonAirMask, visited, queue, localX, localY, localZ - 1, target);
            addRegionNeighbor(nonAirMask, visited, queue, localX, localY, localZ + 1, target);
        }
    }

    private static void addRegionNeighbor(boolean[] nonAirMask, boolean[] visited, ArrayDeque<Integer> queue, int localX, int localY, int localZ, boolean target) {
        if (localX < 0 || localX > 15 || localY < 0 || localY > 15 || localZ < 0 || localZ > 15) {
            return;
        }
        int index = sectionIndex(localX, localY, localZ);
        if (!visited[index] && nonAirMask[index] == target) {
            visited[index] = true;
            queue.add(index);
        }
    }

    private static Map<String, Object> profileMap(GameProfile profile) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", profile.getId() == null ? null : profile.getId().toString());
        map.put("name", profile.getName());
        var properties = new ArrayList<Map<String, Object>>();
        for (Property property : profile.getProperties().values()) {
            var propertyMap = new LinkedHashMap<String, Object>();
            propertyMap.put("name", property.name());
            propertyMap.put("value", property.value());
            propertyMap.put("signature", property.signature());
            propertyMap.put("hasSignature", property.hasSignature());
            properties.add(propertyMap);
        }
        map.put("properties", properties);
        return map;
    }

    private static Map<String, Object> playerMap(Minecraft minecraft, Player player) {
        var map = new LinkedHashMap<String, Object>();
        var playerInfo = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(player.getUUID());
        map.put("score", player.getScore());
        map.put("experienceLevel", player.experienceLevel);
        map.put("totalExperience", player.totalExperience);
        map.put("experienceProgress", player.experienceProgress);
        map.put("mayBuild", player.mayBuild());
        if (playerInfo != null) {
            map.put("gameMode", playerInfo.getGameMode() == null ? null : playerInfo.getGameMode().getName());
            map.put("latency", playerInfo.getLatency());
            map.put("tabListName", playerInfo.getTabListDisplayName() == null ? null : playerInfo.getTabListDisplayName().getString());
        }
        var food = player.getFoodData();
        var foodMap = new LinkedHashMap<String, Object>();
        foodMap.put("level", food.getFoodLevel());
        foodMap.put("saturation", food.getSaturationLevel());
        foodMap.put("exhaustion", food.getExhaustionLevel());
        map.put("food", foodMap);
        var abilities = player.getAbilities();
        var abilitiesMap = new LinkedHashMap<String, Object>();
        abilitiesMap.put("invulnerable", abilities.invulnerable);
        abilitiesMap.put("flying", abilities.flying);
        abilitiesMap.put("mayfly", abilities.mayfly);
        abilitiesMap.put("instabuild", abilities.instabuild);
        abilitiesMap.put("mayBuild", abilities.mayBuild);
        abilitiesMap.put("flyingSpeed", abilities.getFlyingSpeed());
        map.put("abilities", abilitiesMap);
        return map;
    }

    private static Map<String, Object> inventoryMap(Player player, HolderLookup.Provider registryAccess) {
        var inventory = player.getInventory();
        var map = new LinkedHashMap<String, Object>();
        map.put("selected", inventory.selected);
        map.put("selectedItem", RMcpMcDataCodec211.itemStackToMap(inventory.getSelected(), registryAccess));
        map.put("items", itemStackList(inventory.items, registryAccess));
        map.put("armor", itemStackList(inventory.armor, registryAccess));
        map.put("offhand", itemStackList(inventory.offhand, registryAccess));
        return map;
    }

    private static List<Map<String, Object>> itemStackList(List<ItemStack> stacks, HolderLookup.Provider registryAccess) {
        var list = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < stacks.size(); i++) {
            var stack = RMcpMcDataCodec211.itemStackToMap(stacks.get(i), registryAccess);
            if (stack != null) {
                stack.put("slot", i);
                list.add(stack);
            }
        }
        return list;
    }

    private static RMcpLangKeyIndex langKeyIndex(Minecraft minecraft) {
        var resourceManager = minecraft.getResourceManager();
        var cache = langKeyIndex;
        if (cache != null && cache.resourceManager() == resourceManager) {
            return cache.index();
        }
        synchronized (RDIMain.class) {
            cache = langKeyIndex;
            if (cache != null && cache.resourceManager() == resourceManager) {
                return cache.index();
            }
            var english = ClientLanguage.loadFrom(resourceManager, List.of("en_us"), false).getLanguageData();
            var chinese = ClientLanguage.loadFrom(resourceManager, List.of("zh_cn"), false).getLanguageData();
            var index = RMcpLangKeyIndex.create(english, chinese);
            langKeyIndex = new LangKeyIndexCache(resourceManager, index);
            return index;
        }
    }

    private record LangKeyIndexCache(Object resourceManager, RMcpLangKeyIndex index) {
    }

    private record InventorySlotRef(String section, int index, String canonical, int menuSlot) {
    }

    private static final class ResourceAccumulator {
        private final String id;
        private final String category;
        private final LinkedHashMap<String, SectionResourceCount> sections = new LinkedHashMap<>();
        private int count;
        private RMcpBlockPosData nearest;
        private long nearestDistanceSquared = Long.MAX_VALUE;

        private ResourceAccumulator(String id, String category) {
            this.id = id;
            this.category = category;
        }

        private String id() {
            return id;
        }

        private int count() {
            return count;
        }

        private void add(int chunkX, int chunkZ, int sectionY, int blockX, int blockY, int blockZ, int centerX, int centerY, int centerZ) {
            count++;
            var key = chunkX + "," + chunkZ + "," + sectionY;
            sections.computeIfAbsent(key, ignored -> new SectionResourceCount(chunkX, chunkZ, sectionY)).add();
            long dx = blockX - centerX;
            long dy = blockY - centerY;
            long dz = blockZ - centerZ;
            long distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = new RMcpBlockPosData(blockX, blockY, blockZ);
            }
        }

        private RMcpNearbyResourcesData.Resource data() {
            var sectionData = sections.values().stream()
                    .sorted(Comparator.comparingInt(SectionResourceCount::count).reversed()
                            .thenComparingInt(SectionResourceCount::chunkX)
                            .thenComparingInt(SectionResourceCount::chunkZ)
                            .thenComparingInt(SectionResourceCount::sectionY))
                    .limit(12)
                    .map(SectionResourceCount::data)
                    .toList();
            return new RMcpNearbyResourcesData.Resource(id, category, count, nearest, sectionData);
        }
    }

    private static final class SectionResourceCount {
        private final int chunkX;
        private final int chunkZ;
        private final int sectionY;
        private int count;

        private SectionResourceCount(int chunkX, int chunkZ, int sectionY) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.sectionY = sectionY;
        }

        private int chunkX() {
            return chunkX;
        }

        private int chunkZ() {
            return chunkZ;
        }

        private int sectionY() {
            return sectionY;
        }

        private int count() {
            return count;
        }

        private void add() {
            count++;
        }

        private RMcpNearbyResourcesData.ResourceSection data() {
            return new RMcpNearbyResourcesData.ResourceSection(chunkX, chunkZ, sectionY, count);
        }
    }

    private record SectionAnalysis(
            RMcpSectionSemanticData.Summary summary,
            List<RMcpSectionSemanticData.Layer> layers,
            Map<String, String> legend,
            RMcpSectionSemanticData.Features features,
            Map<String, Integer> counts,
            Integer minNonAirY,
            Integer maxNonAirY
    ) {
    }

}
