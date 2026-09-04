package com.kalloer1.p2p.client;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.client.ClientBindState;
import com.kalloer1.p2p.client.screen.ChannelScreen;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** FORGE-bus client events: open the config screen, draw endpoint highlight boxes + bound-face glow,
 *  and draw a see-through (x-ray) red frame around a block the player jump-highlighted from the GUI. */
@Mod.EventBusSubscriber(modid = P2P.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private static final int HUD_RANGE = 32;

    private ClientForgeEvents() {}

    /**
     * 端点面光 + 跳转高亮框的渲染类型。配方照抄 SFM（Super Factory Manager）标签枪方块高亮
     * （TeamDman/SuperFactoryManager 1.20.1, ItemWorldRenderer.java:47-75），那是玩家实测
     * 「光影开/关都能透视显示」的活证据：
     *  - 纹理状态绑**方块图集**：Iris/Oculus 会把这类 pass 归入自己的世界管线（textured 通道）
     *    而不是丢弃——无纹理的 debugQuads / 裸 position_color 立即模式都会被光影区别对待；
     *  - depthTest always(519)：恒定穿墙 X 光（深度恒通过），不需要手工 GL 状态；
     *  - 透明度 **SRC_ALPHA→ONE 加法混合**：光是「加上去发光」而不是「盖上去被亮背景稀释」，
     *    光影 HDR/曝光后的天空与发光体前也不会消失（标准 alpha 混合在 0.22 低透明度下会被吃掉）；
     *  - cull 关闭：X 光穿墙时方块背面的绑定面也要可见；
     *  - 阶段用 AFTER_PARTICLES（SFM 同款）：此时仍在世界渲染管线内、bufferSource 正常提交。
     * 颜色/alpha 语义（IN 红 1/0.15/0.15、OUT 蓝 0.25/0.45/1、hover 黄、当前频道更亮）全部保留。
     */
    private static final RenderType GLOW_RT = RenderType.create(
            "p2p_overlay",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setDepthTestState(new RenderStateShard.DepthTestStateShard("always", 519))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("p2p_additive", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .createCompositeState(true)
    );

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

    /** 端点面片沿法线外推的距离，避免和方块表面 z-fighting。 */
    private static final float FACE_EPS = 0.015f;

    /**
     * 专用的覆盖层 bufferSource——**不用** `mc.renderBuffers().bufferSource()`。
     *
     * Oculus（Iris）开启时会用 FullyBufferedMultiBufferSource 替换共享 bufferSource：endBatch 只把
     * 段排进批处理队列，真正上传延迟到某人全量 flush 的时机（实测中 mcjtylib 的 DelayedRenderer
     * 会在 Iris 阴影 pass 派发的 RenderLevelStageEvent 里全量 flush——阴影 pass 没有主渲染的 shader
     * 程序，段上传时 shader 为 null → VertexBuffer NPE，整帧崩溃，且栈帧落在 flush 执行者身上，
     * 看起来与 p2p 无关）。自建 source 的 endBatch 走 BufferUploader 立即上传，不产生延迟段，
     * 从根上避开这条崩溃通道。
     */
    private static final MultiBufferSource.BufferSource OVERLAY_SRC =
            MultiBufferSource.immediate(new BufferBuilder(1536));

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
        var bs = OVERLAY_SRC;
        Vec3 cam = event.getCamera().getPosition();

        VertexConsumer vc = bs.getBuffer(GLOW_RT);
        try {
            // (1) GUI "jump to block" highlight — drawn regardless of bind mode.
            drawHighlight(pose, vc, cam);

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
            P2P.LOGGER.debug("[p2p] overlay rendered members={} boundCid={} hover={}/{}", rendered, boundCid, hoverPos, hoverFace);
        } catch (Throwable ex) {
            // Defensive: a broken overlay must never crash the client.
            P2P.LOGGER.error("[p2p] endpoint overlay render failed; skipping this frame", ex);
        } finally {
            bs.endBatch(GLOW_RT);
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
        MutableComponent bindText = Component.empty()
                .append(Lang.t(Lang.HUD_BIND))
                .append(ch >= 0 ? Lang.t(Lang.HUD_BIND_CHANNEL, ch) : Lang.t(Lang.HUD_BIND_NO_CHANNEL))
                .append(Lang.t(Lang.HUD_HINT));
        int pad = 6, fh = 10;
        int boxW = mc.font.width(bindText) + pad * 2;
        int boxX = (w - boxW) / 2;
        // 上移到快捷栏之上（快捷栏占底部约 22px，状态条自身高 22px，留 2px 间距）
        int boxY = mc.getWindow().getGuiScaledHeight() - 46;
        gg.fill(boxX, boxY, boxX + boxW, boxY + fh + pad * 2, 0xCC2f2f2f);
        gg.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFFFFFFFF);
        gg.fill(boxX, boxY + fh + pad * 2 - 1, boxX + boxW, boxY + fh + pad * 2, 0xFF555555);
        gg.drawString(mc.font, bindText.withStyle(ch >= 0 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW),
                boxX + pad, boxY + pad, 0xFFFFFFFF, false);
    }

    private static void drawHighlight(PoseStack pose, VertexConsumer vc, Vec3 cam) {
        BlockPos p = ClientChannelCache.INSTANCE.getHighlightPos();
        if (p == null) return;
        if (System.currentTimeMillis() > ClientChannelCache.INSTANCE.getHighlightExpire()) {
            ClientChannelCache.INSTANCE.clearHighlight();
            return;
        }
        drawHighlightBox(pose, vc, p, cam);
    }

    /**
     * Draw a see-through red cube around the highlighted block (6 faces, filled, additive glow). The
     * FACE_QUAD corners are perimeter-ordered so each face is a proper convex quad. Redrawn every frame
     * while the highlight is fresh (expiry handled by the caller).
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

    /** Draw a translucent filled face as a POSITION_COLOR quad (additive glow, depth always passes = x-ray). */
    private static void faceGlow(PoseStack pose, VertexConsumer vc, BlockPos pos, Direction face,
                                 float r, float g, float b, float a, Vec3 cam) {
        var m = pose.last().pose();
        float[][] quad = FACE_QUAD[face.ordinal()];
        float[] n = FACE_NORMAL[face.ordinal()];
        for (int i = 0; i < 4; i++) {
            float x = (float)(pos.getX() + quad[i][0] + n[0] * FACE_EPS - cam.x);
            float y = (float)(pos.getY() + quad[i][1] + n[1] * FACE_EPS - cam.y);
            float z = (float)(pos.getZ() + quad[i][2] + n[2] * FACE_EPS - cam.z);
            vc.vertex(m, x, y, z).color(r, g, b, a).endVertex();
        }
    }
}
