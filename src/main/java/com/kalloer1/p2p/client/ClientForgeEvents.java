package com.kalloer1.p2p.client;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.kalloer1.p2p.client.screen.ChannelScreen;
import com.kalloer1.p2p.client.screen.P2PTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** FORGE-bus client events: open the config screen, draw endpoint highlight boxes + bound-face glow,
 *  and draw a see-through (x-ray) red frame around a block the player jump-highlighted from the GUI. */
@EventBusSubscriber(modid = P2P.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final int HUD_RANGE = 32;
    /** 绑定 HUD 胶囊底色（spec v1.2：iOS 深色半透明 0xC01C1C1E）。 */
    private static final int HUD_CAPSULE_BG = 0xC01C1C1E;

    private ClientForgeEvents() {}

    private static final float LINE_PX = 4.0f;

    private static VertexBuffer overlayVbo = null;

    // 柔和色板（用户选定方案 A）：填充低饱和、描边同色系更亮一档。色相语义：IN 红、OUT 蓝。
    // 1.4.0：绑定模式的黄色 hover 预览高亮（HOVER_FILL/HOVER_LINE）已按要求完全移除；
    // 已绑定端点的红/蓝面高亮与双击定位红框保留。
    private static final float[] INPUT_FILL = {0.898f, 0.451f, 0.451f};   // #E57373 珊瑚红
    private static final float[] INPUT_LINE = {1.000f, 0.620f, 0.620f};   // #FF9E9E
    private static final float[] OUTPUT_FILL = {0.392f, 0.710f, 0.965f};  // #64B5F6 天蓝
    private static final float[] OUTPUT_LINE = {0.620f, 0.850f, 1.000f};  // #9ED9FF
    private static final float[] JUMP_FILL = {1.000f, 0.120f, 0.120f};
    private static final float[] JUMP_LINE = {1.000f, 0.400f, 0.400f};

    private static final float[][][] FACE_QUAD = {
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},           // DOWN
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},           // UP
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},           // NORTH
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},           // SOUTH
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},           // WEST
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},           // EAST
    };
    private static final float[][] FACE_NORMAL = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
    };

    private static final float FACE_EPS = 0.015f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            if (Keybinds.OPEN_GUI.consumeClick()) {
                P2P.LOGGER.info("[p2p] opening ChannelScreen via keybind");
                PacketDistributor.sendToServer(new RequestChannelsC2SPacket());
                mc.setScreen(new ChannelScreen());
            } else if (Keybinds.BIND_MODE.consumeClick()) {
                P2P.LOGGER.info("[p2p] toggling bind mode via keybind");
                ClientBindState.toggle();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cam = event.getCamera().getPosition();

        try {
            // ---- 收集几何：绝对世界坐标，每帧重建 ----
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            // (1) GUI "jump to block" highlight
            drawHighlight(cam, builder);

            // (2) Bound-face glow — only for current bound channel
            if (ClientBindState.isActive()) {
                int boundCid = ClientBindState.getBoundChannel();
                CompoundTag data = ClientChannelCache.INSTANCE.getData();
                if (data != null && boundCid >= 0) {
                    ResourceLocation dim = mc.level.dimension().location();
                    BlockPos ppos = mc.player.blockPosition();

                    HitResult hit = mc.hitResult;
                    BlockPos hoverPos = (hit instanceof BlockHitResult bhr) ? bhr.getBlockPos() : null;
                    Direction hoverFace = (hit instanceof BlockHitResult bhr) ? bhr.getDirection() : null;

                    int rendered = 0;
                    for (Tag t : data.getList("channels", Tag.TAG_COMPOUND)) {
                        Channel c = Channel.deserializeNBT((CompoundTag) t, mc.level.registryAccess());
                        if (c.id != boundCid) continue;
                        for (Member m : c.members) {
                            if (!m.dim.equals(dim)) continue;
                            if (m.pos.distSqr(ppos) > HUD_RANGE * HUD_RANGE) continue;
                            float[] fill = roleFill(m.role);
                            float[] line = roleLine(m.role);
                            float a = 0.65f;
                            pushFace(builder, m.pos, m.face, fill, line, a, cam, mc);
                            rendered++;
                            if (hoverPos != null && m.pos.equals(hoverPos) && m.face == hoverFace) {
                                // 仅提亮"已绑定的端点面"（红/蓝，保留）；未绑定的准星面不再画黄色预览框
                                pushFace(builder, m.pos, m.face, fill, line, 0.85f, cam, mc);
                            }
                        }
                    }
                    // 1.4.0：原来这里会对未绑定的准星面 pushFace(HOVER_*) 画黄色预览框，已整体移除。
                    P2P.LOGGER.debug("[p2p] overlay built faces={} boundCid={} hover={}/{}", rendered, boundCid, hoverPos, hoverFace);
                }
            }

            // ---- 上传到常驻 VBO ----
            // A frame without any highlight/endpoint geometry is valid. Do not call
            // buildOrThrow(), because NeoForge 1.21.1 throws for an empty builder.
            MeshData mesh = builder.build();
            if (mesh == null) return;
            if (overlayVbo == null) overlayVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            overlayVbo.bind();
            overlayVbo.upload(mesh);
            VertexBuffer.unbind();

            // ---- 绘制：矩阵作为参数直传 + 原版 GL 关深度 ----
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // RenderLevelStageEvent creates an empty PoseStack for block stages such as
            // AFTER_CUTOUT_BLOCKS. Use the stage model-view matrix (camera rotation)
            // and add the camera translation for our absolute world-space vertices.
            Matrix4f modelView = new Matrix4f(event.getModelViewMatrix())
                    .translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
            overlayVbo.bind();
            overlayVbo.drawWithShader(modelView, new Matrix4f(event.getProjectionMatrix()),
                    RenderSystem.getShader());
            VertexBuffer.unbind();

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);
        } catch (Throwable ex) {
            P2P.LOGGER.error("[p2p] endpoint overlay render failed; skipping this frame", ex);
        }
    }

    private static void drawHighlight(Vec3 cam, BufferBuilder builder) {
        BlockPos p = ClientChannelCache.INSTANCE.getHighlightPos();
        if (p == null) return;
        if (System.currentTimeMillis() > ClientChannelCache.INSTANCE.getHighlightExpire()) {
            ClientChannelCache.INSTANCE.clearHighlight();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (int d = 0; d < 6; d++) {
            pushFace(builder, p, Direction.values()[d], JUMP_FILL, JUMP_LINE, 0.35f, cam, mc);
        }
    }

    private static float[] roleFill(Role role) {
        return role == Role.INPUT ? INPUT_FILL : OUTPUT_FILL;
    }

    private static float[] roleLine(Role role) {
        return role == Role.INPUT ? INPUT_LINE : OUTPUT_LINE;
    }

    private static float px2World(Minecraft mc) {
        return (float) (2 * Math.tan(Math.toRadians(mc.options.fov().get()) / 2) / mc.getWindow().getHeight());
    }

    private static void pushFace(BufferBuilder builder, BlockPos pos, Direction face, float[] fill, float[] line, float a, Vec3 cam, Minecraft mc) {
        float[][] quad = FACE_QUAD[face.ordinal()];
        float[] n = FACE_NORMAL[face.ordinal()];
        float la = Math.min(1f, a + 0.25f);

        Vec3[] c = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            c[i] = new Vec3(pos.getX() + quad[i][0] + n[0] * FACE_EPS,
                    pos.getY() + quad[i][1] + n[1] * FACE_EPS,
                    pos.getZ() + quad[i][2] + n[2] * FACE_EPS);
        }
        pushQuad(builder, c, fill[0], fill[1], fill[2], a);

        for (int i = 0; i < 4; i++) {
            Vec3 p0 = c[i], p1 = c[(i + 1) & 3];
            Vec3 d = p1.subtract(p0);
            double len = d.length();
            if (len < 1.0e-6) continue;
            d = d.scale(1.0 / len);
            Vec3 mid = p0.add(p1).scale(0.5);
            Vec3 view = mid.subtract(cam);
            double viewLen = view.length();
            if (viewLen < 1.0e-6) continue;
            view = view.scale(1.0 / viewLen);
            Vec3 perp = d.cross(view);
            double perpLen = perp.length();
            if (perpLen < 1.0e-6) {
                perp = new Vec3(n[0], n[1], n[2]).cross(d);
                perpLen = perp.length();
                if (perpLen < 1.0e-6) continue;
            }
            perp = perp.scale(1.0 / perpLen);
            float halfW = (float) (LINE_PX * 0.5 * px2World(mc) * viewLen);
            Vec3 o = perp.scale(halfW);
            Vec3[] strip = {p0.subtract(o), p0.add(o), p1.add(o), p1.subtract(o)};
            pushQuad(builder, strip, line[0], line[1], line[2], la);
        }
    }

    private static void pushQuad(BufferBuilder builder, Vec3[] corners, float r, float g, float b, float a) {
        for (Vec3 p : corners) {
            builder.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
        }
    }

    /**
     * 绑定模式开启时在屏幕底部居中画一条 iOS 深色半透明胶囊状态条（spec v1.2：文案不变只换皮）——
     * "P2P 绑定模式"白色加粗（单绘 + 阴影）、"频道 #N"用 TINT 蓝点缀、操作提示用 TEXT_SECOND 浅灰。
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (!ClientBindState.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics gg = evt.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int ch = ClientBindState.getBoundChannel();
        MutableComponent title = Lang.t(Lang.HUD_BIND);
        MutableComponent channelPart = ch >= 0 ? Lang.t(Lang.HUD_BIND_CHANNEL, ch) : Lang.t(Lang.HUD_BIND_NO_CHANNEL);
        MutableComponent hint = Lang.t(Lang.HUD_HINT);
        int padX = 10;
        int boxH = mc.font.lineHeight + 10;
        int boxW = padX * 2 + mc.font.width(title) + mc.font.width(channelPart) + mc.font.width(hint);
        int boxX = (w - boxW) / 2;
        int boxY = mc.getWindow().getGuiScaledHeight() - 46;
        int textY = boxY + (boxH - mc.font.lineHeight) / 2;
        P2PTheme.drawRoundRect(gg, boxX, boxY, boxW, boxH, boxH / 2, HUD_CAPSULE_BG);
        int x = boxX + padX;
        P2PTheme.drawStringBold(gg, mc.font, title, x, textY, P2PTheme.CARD);
        x += mc.font.width(title);
        x += gg.drawString(mc.font, channelPart, x, textY,
                ch >= 0 ? P2PTheme.TINT : P2PTheme.WARNING, false);
        gg.drawString(mc.font, hint, x, textY, P2PTheme.TEXT_SECOND, false);
    }
}
