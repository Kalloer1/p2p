package com.kalloer1.p2p.client;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.client.ClientBindState;
import com.kalloer1.p2p.client.screen.ChannelScreen;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.OptionalDouble;

/** FORGE-bus client events: open the config screen, draw endpoint highlight boxes + bound-face glow,
 *  and draw a see-through (x-ray) red frame around a block the player jump-highlighted from the GUI. */
@Mod.EventBusSubscriber(modid = P2P.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private static final int HUD_RANGE = 32;

    private ClientForgeEvents() {}

    // Vanilla's debug quad type: POSITION_COLOR, QUADS, translucent, no cull.
    // Each face is exactly 4 vertices in PERIMETER order, so QUADS mode draws a full, non-bowtied face.
    private static final RenderType GLOW_RT = RenderType.debugQuads();

    // Block-space corner offsets (0..1) for each face, ordered around the perimeter (NOT a triangle strip).
    // A triangle-strip ordering drawn in QUADS mode makes a self-intersecting "bowtie" that drops a triangle;
    // perimeter order gives a proper convex quad, so every bound face renders fully.
    // Indexed by Direction.ordinal() (DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5).
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

    // See-through red box for the GUI-driven "jump to block" highlight. Reuses debugQuads() (a public
    // RenderType that already disables the depth test and raises the view layer, so it draws through terrain
    // as an x-ray) to avoid the protected RenderStateShard constants and the line-only RenderType access limits.
    private static final RenderType XRAY_RT = GLOW_RT;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            if (Keybinds.OPEN_GUI.consumeClick()) {
                P2P.LOGGER.info("[p2p] opening ChannelScreen via keybind");
                ModMessages.INSTANCE.sendToServer(new RequestChannelsC2SPacket());
                mc.setScreen(new ChannelScreen());
            } else if (Keybinds.BIND_MODE.consumeClick()) {
                P2P.LOGGER.info("[p2p] toggling bind mode via keybind");
                ClientBindState.toggle();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        PoseStack pose = event.getPoseStack();
        var bs = mc.renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();

        // (1) GUI "jump to block" highlight — drawn regardless of bind mode.
        drawHighlight(pose, bs, cam);

        // (2) Bound-face glow — only while the bind mode is active (the old wrench is gone).
        if (!ClientBindState.isActive()) return;
        int boundCid = ClientBindState.getBoundChannel();
        CompoundTag data = ClientChannelCache.INSTANCE.getData();
        if (data == null) return;

        ResourceLocation dim = mc.level.dimension().location();
        BlockPos ppos = mc.player.blockPosition();

        HitResult hit = mc.hitResult;
        BlockPos hoverPos = (hit instanceof BlockHitResult bhr) ? bhr.getBlockPos() : null;
        Direction hoverFace = (hit instanceof BlockHitResult bhr) ? bhr.getDirection() : null;

        VertexConsumer vc = bs.getBuffer(GLOW_RT);
        try {
            boolean hoverBound = false;
            int rendered = 0;
            for (Tag t : data.getList("channels", Tag.TAG_COMPOUND)) {
                Channel c = Channel.deserializeNBT((CompoundTag) t);
                for (Member m : c.members) {
                    if (!m.dim.equals(dim)) continue;
                    if (m.pos.distSqr(ppos) > HUD_RANGE * HUD_RANGE) continue;
                    float[] col = roleColor(m.role);
                    // 当前绑定频道的端点画得更亮，方便确认"这次会绑到哪个频道"
                    boolean isCurrent = (boundCid >= 0 && c.id == boundCid);
                    float a = isCurrent ? 0.7f : 0.22f;
                    faceGlow(pose, vc, m.pos, m.face, col[0], col[1], col[2], a, cam);
                    rendered++;
                    if (hoverPos != null && m.pos.equals(hoverPos) && m.face == hoverFace) {
                        faceGlow(pose, vc, m.pos, m.face, col[0], col[1], col[2], 0.85f, cam);
                        hoverBound = true;
                    }
                }
            }
            if (hoverPos != null && hoverFace != null && !hoverBound) {
                faceGlow(pose, vc, hoverPos, hoverFace, 1f, 0.95f, 0.35f, 0.6f, cam);
            }
            bs.endBatch(GLOW_RT);
            P2P.LOGGER.debug("[p2p] overlay rendered members={} boundCid={} hover={}/{}", rendered, boundCid, hoverPos, hoverFace);
        } catch (Throwable ex) {
            // Defensive: a broken overlay must never crash the client.
            P2P.LOGGER.error("[p2p] endpoint overlay render failed; skipping this frame", ex);
        }
    }

    /** 绑定模式开启时在屏幕底部画一条状态条：当前频道 + 操作提示。 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (!ClientBindState.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics gg = evt.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int ch = ClientBindState.getBoundChannel();
        String text = "P2P 绑定模式" + (ch >= 0 ? " · 频道 #" + ch : " · 未选当前频道")
                + "  |  右键=输入  左键=输出  Ctrl=连锁  (再按 B 退出)";
        int pad = 6, fh = 10;
        int boxW = mc.font.width(text) + pad * 2;
        int boxX = (w - boxW) / 2;
        int boxY = mc.getWindow().getGuiScaledHeight() - 24;
        gg.fill(boxX, boxY, boxX + boxW, boxY + fh + pad * 2, 0xCC2f2f2f);
        gg.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFFFFFFFF);
        gg.fill(boxX, boxY + fh + pad * 2 - 1, boxX + boxW, boxY + fh + pad * 2, 0xFF555555);
        gg.drawString(mc.font, Component.literal(text).withStyle(ch >= 0 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW),
                boxX + pad, boxY + pad, 0xFFFFFFFF, false);
    }

    private static void drawHighlight(PoseStack pose, MultiBufferSource.BufferSource bs, Vec3 cam) {
        BlockPos p = ClientChannelCache.INSTANCE.getHighlightPos();
        if (p == null) return;
        if (System.currentTimeMillis() > ClientChannelCache.INSTANCE.getHighlightExpire()) {
            ClientChannelCache.INSTANCE.clearHighlight();
            return;
        }
        VertexConsumer vc = bs.getBuffer(XRAY_RT);
        drawHighlightBox(pose, vc, p, cam);
        bs.endBatch(XRAY_RT);
    }

    /**
     * Draw a see-through red cube around the highlighted block (6 faces, filled, translucent). Uses
     * debugQuads() so it renders through terrain (x-ray) without touching the protected RenderStateShard
     * constants. The FACE_QUAD corners are perimeter-ordered so each face is a proper convex quad.
     */
    private static void drawHighlightBox(PoseStack pose, VertexConsumer vc, BlockPos pos, Vec3 cam) {
        var m = pose.last().pose();
        for (int d = 0; d < 6; d++) {
            float[][] quad = FACE_QUAD[d];
            for (int i = 0; i < 4; i++) {
                float x = (float) (pos.getX() + quad[i][0] - cam.x);
                float y = (float) (pos.getY() + quad[i][1] - cam.y);
                float z = (float) (pos.getZ() + quad[i][2] - cam.z);
                vc.vertex(m, x, y, z).color(1f, 0.12f, 0.12f, 0.35f).endVertex();
            }
        }
    }

    /** Color per role: INPUT = red, OUTPUT = blue. */
    private static float[] roleColor(Role role) {
        return role == Role.INPUT ? new float[]{1f, 0.15f, 0.15f} : new float[]{0.25f, 0.45f, 1f};
    }

    /** Draw a translucent filled face as a POSITION_COLOR quad (debugQuads mode, no cull). */
    private static void faceGlow(PoseStack pose, VertexConsumer vc, BlockPos pos, Direction face,
                                 float r, float g, float b, float a, Vec3 cam) {
        var m = pose.last().pose();
        float[][] quad = FACE_QUAD[face.ordinal()];
        float[] n = FACE_NORMAL[face.ordinal()];
        float eps = 0.015f;
        for (int i = 0; i < 4; i++) {
            float x = (float)(pos.getX() + quad[i][0] + n[0] * eps - cam.x);
            float y = (float)(pos.getY() + quad[i][1] + n[1] * eps - cam.y);
            float z = (float)(pos.getZ() + quad[i][2] + n[2] * eps - cam.z);
            vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
        }
    }
}
