package calebxzhou.rdi.mc.client;

import calebxzhou.rdi.mc.client.rcmd.RcmdClientCommands;
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpFluidData;
import calebxzhou.rdi.mc.common2.mcp.RMcpGameConnector;
import calebxzhou.rdi.mc.common2.mcp.RMcpHttpServer;
import calebxzhou.rdi.mc.common2.mcp.RMcpPosData;
import calebxzhou.rdi.mc.common2.mcp.RMcpStaringBlockData;
import calebxzhou.rdi.mc.common2.mcp.RMcpTestData;
import calebxzhou.rdi.mc.common.RDI;
import com.google.common.net.HostAndPort;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.Commands;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
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

import static calebxzhou.rdi.mc.common.RDI.GAME_IP;
import static calebxzhou.rdi.mc.common.RDI.HOST_NAME;

/**
 * calebxzhou @ 2026-01-10 22:33
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi",value = Dist.CLIENT)
public class RDIMain {
    private static final Logger LGR = LoggerFactory.getLogger("rdi-mcp");

    public RDIMain() {
        try {
            RMcpHttpServer.start(new RMcpGameConnector() {
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
                            fluid = new RMcpFluidData(fluidId, stateString(fluidId, fluidState.toString()));
                        }
                    }
                    return new RMcpStaringBlockData(
                            minecraft.level.dimension().location().toString(),
                            new RMcpBlockPosData(pos.getX(), pos.getY(), pos.getZ()),
                            blockId,
                            stateString(blockId, blockState.toString()),
                            fluid
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

    private static String stateString(String id, String raw) {
        int propertyStart = raw.indexOf('[');
        return propertyStart < 0 ? id : id + raw.substring(propertyStart);
    }
}
