package calebxzhou.rdi.mc.client;

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
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import static calebxzhou.rdi.mc.common.RDI.GAME_IP;
import static calebxzhou.rdi.mc.common.RDI.HOST_NAME;

/**
 * calebxzhou @ 2026-01-10 22:33
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi",value = Dist.CLIENT)
public class RDIMain {
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
}
