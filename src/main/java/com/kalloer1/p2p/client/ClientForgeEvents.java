package com.kalloer1.p2p.client;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.Member;
import com.kalloer1.p2p.channel.Role;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.kalloer1.p2p.client.screen.ChannelScreen;
import com.kalloer1.p2p.client.screen.P2PTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
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
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** FORGE-bus client events: open the config screen, draw endpoint highlight boxes + bound-face glow,
 *  and draw a see-through (x-ray) red frame around a block the player jump-highlighted from the GUI.
 *
 *  <p>Oculus（Iris）安全说明（1.20.1 专属修复，commit 61164f2 的延续）：本渲染路径<b>绝不触碰</b>
 *  {@code mc.renderBuffers().bufferSource()}——Oculus 开启时会把共享 bufferSource 换成
 *  FullyBufferedMultiBufferSource，endBatch 只入批处理队列，若由 Iris 阴影 pass 派发的 flush
 *  上传这些段，会在无主渲染 shader 的阴影 pass 里 NPE 整帧崩溃。这里改用私有
 *  {@link VertexBuffer} + {@code drawWithShader(mv, proj, shader)}（矩阵作为参数直传），
 *  上传路径走 BufferUploader 立即执行，不产生延迟段，从根上避开那条崩溃通道。 */
@Mod.EventBusSubscriber(modid = P2P.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private static final int HUD_RANGE = 32;
    /** 绑定 HUD 胶囊底色（spec v1.2：iOS 深色半透明 0xC01C1C1E）。 */
    private static final int HUD_CAPSULE_BG = 0xC01C1C1E;

    private ClientForgeEvents() {}

    /**
     * 覆盖层渲染管线（详见 workspace docs/adr/0002-overlay-renders-in-gui-phase.md）——
     * **配方照抄 Advanced XRay**（AdvancedXRay/XRay-Mod mc/1.20, Render.java，久经光影实测）：
     *
     * <ol>
     *   <li>阶段 {@code RenderLevelStageEvent.AFTER_CUTOUT_BLOCKS}（XRay 同款）；</li>
     *   <li>几何用**绝对世界坐标**每帧重建进私有 BufferBuilder，上传到常驻 {@link VertexBuffer}；</li>
     *   <li>绘制：**矩阵作为参数直接传** {@code vertexBuffer.drawWithShader(mv, proj, shader)}
     *       ——绝不经过 RenderSystem 全局矩阵（Oculus 会改写它们：矩阵走全局 → 覆盖层跟着视角转）；</li>
     *   <li>深度测试用**原版 GL 调用** {@code GL11.glDisable(GL_DEPTH_TEST)} 关闭（与 GlStateManager
     *       缓存不互相污染），穿墙 X 光稳定；</li>
     *   <li>混合用**普通 alpha**（SRC_ALPHA / ONE_MINUS_SRC_ALPHA），**不是加法**——加法混合
     *       会被亮背景（天空/HDR）整体吃掉，实测表现为「视角一转向亮处颜色就消失」。</li>
     * </ol>
     *
     * 描边为「相机朝向细四边形」：每条边沿 (边方向×视线方向) 的垂直方向撑开 {@link #LINE_PX}
     * 屏幕像素宽——掠射角下 TAA 溶解不了它。alpha 语义（IN 红、OUT 蓝、hover 黄、当前频道更亮）保留。
     */
    private static final float LINE_PX = 4.0f;

    private static final BufferBuilder OVERLAY_BUF = new BufferBuilder(2048);
    private static boolean bufBuilding = false;
    private static VertexBuffer overlayVbo = null;

    // 柔和色板（用户选定方案 A）：填充低饱和、描边同色系更亮一档。色相语义：IN 红、OUT 蓝。
    // 1.4.0：绑定模式的黄色 hover 预览高亮（HOVER_FILL/HOVER_LINE）已按要求完全移除；
    // 已绑定端点的红/蓝面高亮与双击定位红框保留。
    private static final float[] INPUT_FILL = {0.898f, 0.451f, 0.451f};   // #E57373 珊瑚红
    private static final float[] INPUT_LINE = {1.000f, 0.620f, 0.620f};   // #FF9E9E
    private static final float[] OUTPUT_FILL = {0.392f, 0.710f, 0.965f};  // #64B5F6 天蓝
    private static final float[] OUTPUT_LINE = {0.620f, 0.850f, 1.000f};  // #9ED9FF
    // GUI 跳转高亮框（非绑定模式 feature，保持红色语义，同样走面+描边）
    private static final float[] JUMP_FILL = {1.000f, 0.120f, 0.120f};
    private static final float[] JUMP_LINE = {1.000f, 0.400f, 0.400f};

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

    /** 端点面片沿法线外推的距离，避免和方块表面 z-fighting（无深度写入时仅为视觉分离）。 */
    private static final float FACE_EPS = 0.015f;

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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cam = event.getCamera().getPosition();

        try {
            // ---- 收集几何：绝对世界坐标，每帧重建（端点/hover 都会变）----
            OVERLAY_BUF.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            bufBuilding = true;

            // (1) GUI "jump to block" highlight — drawn regardless of bind mode.
            drawHighlight(cam);

            // (2) Bound-face glow — only while the bind mode is active, and ONLY for the current
            //     bound channel (用户反馈：不应显示所有频道的端点).
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
                        Channel c = Channel.deserializeNBT((CompoundTag) t);
                        if (c.id != boundCid) continue;   // 只画当前绑定频道
                        for (Member m : c.members) {
                            if (!m.dim.equals(dim)) continue;
                            if (m.pos.distSqr(ppos) > HUD_RANGE * HUD_RANGE) continue;
                            float[] fill = roleFill(m.role);
                            float[] line = roleLine(m.role);
                            float a = 0.65f;
                            pushFace(m.pos, m.face, fill, line, a, cam, mc);
                            rendered++;
                            if (hoverPos != null && m.pos.equals(hoverPos) && m.face == hoverFace) {
                                // 仅提亮"已绑定的端点面"（红/蓝，保留）；未绑定的准星面不再画黄色预览框
                                pushFace(m.pos, m.face, fill, line, 0.85f, cam, mc);
                            }
                        }
                    }
                    // 1.4.0：原来这里会对未绑定的准星面 pushFace(HOVER_*) 画黄色预览框，已整体移除。
                    P2P.LOGGER.debug("[p2p] overlay built faces={} boundCid={} hover={}/{}", rendered, boundCid, hoverPos, hoverFace);
                }
            }
            bufBuilding = false;

            // ---- 上传到常驻 VBO（XRay 同款：静态用法，逐帧重传）----
            if (overlayVbo == null) overlayVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            overlayVbo.bind();
            overlayVbo.upload(OVERLAY_BUF.end());
            VertexBuffer.unbind();

            // ---- 绘制：矩阵作为参数直传 + 原版 GL 关深度（XRay 配方，见类注释）----
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            PoseStack matrix = event.getPoseStack();
            matrix.pushPose();
            matrix.translate(-cam.x, -cam.y, -cam.z);
            overlayVbo.bind();
            overlayVbo.drawWithShader(matrix.last().pose(), new Matrix4f(event.getProjectionMatrix()),
                    RenderSystem.getShader());
            VertexBuffer.unbind();
            matrix.popPose();

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_CULL_FACE);
        } catch (Throwable ex) {
            // Defensive: a broken overlay must never crash the client.
            P2P.LOGGER.error("[p2p] endpoint overlay render failed; skipping this frame", ex);
        } finally {
            if (bufBuilding) {
                OVERLAY_BUF.discard();
                bufBuilding = false;
            }
        }
    }

    private static void drawHighlight(Vec3 cam) {
        BlockPos p = ClientChannelCache.INSTANCE.getHighlightPos();
        if (p == null) return;
        if (System.currentTimeMillis() > ClientChannelCache.INSTANCE.getHighlightExpire()) {
            ClientChannelCache.INSTANCE.clearHighlight();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (int d = 0; d < 6; d++) {
            pushFace(p, Direction.values()[d], JUMP_FILL, JUMP_LINE, 0.35f, cam, mc);
        }
    }

    /** Fill color per role: INPUT = soft coral red, OUTPUT = soft sky blue. */
    private static float[] roleFill(Role role) {
        return role == Role.INPUT ? INPUT_FILL : OUTPUT_FILL;
    }

    /** Outline color per role: same hue one step brighter than the fill. */
    private static float[] roleLine(Role role) {
        return role == Role.INPUT ? INPUT_LINE : OUTPUT_LINE;
    }

    /** 每世界单位对应的屏幕像素换算系数：2·tan(fov/2) / 屏幕高度。线宽 = LINE_PX · 系数 · 距离。 */
    private static float px2World(Minecraft mc) {
        return (float) (2 * Math.tan(Math.toRadians(mc.options.fov().get()) / 2) / mc.getWindow().getHeight());
    }

    /**
     * 生成一个端点面的填充 + 4 条描边四边形（绝对世界坐标写入 {@link #OVERLAY_BUF}）。
     * 填充为面四边形本身（沿法线外推 FACE_EPS）；描边为每条边沿 (边方向×视线方向) 垂直方向
     * 撑开 LINE_PX 屏幕像素的细条——掠射角下填充缩成线时描边依然可见。
     */
    private static void pushFace(BlockPos pos, Direction face, float[] fill, float[] line, float a, Vec3 cam, Minecraft mc) {
        float[][] quad = FACE_QUAD[face.ordinal()];
        float[] n = FACE_NORMAL[face.ordinal()];
        float la = Math.min(1f, a + 0.25f);

        // 面四角的绝对世界坐标（沿法线外推 FACE_EPS）。
        Vec3[] c = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            c[i] = new Vec3(pos.getX() + quad[i][0] + n[0] * FACE_EPS,
                    pos.getY() + quad[i][1] + n[1] * FACE_EPS,
                    pos.getZ() + quad[i][2] + n[2] * FACE_EPS);
        }
        pushQuad(c, fill[0], fill[1], fill[2], a);

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
                // 视线与边平行（边在屏幕上缩成一个点）：任意面内垂直方向都行，用法线 × 边兜底。
                perp = new Vec3(n[0], n[1], n[2]).cross(d);
                perpLen = perp.length();
                if (perpLen < 1.0e-6) continue;
            }
            perp = perp.scale(1.0 / perpLen);
            // 固定屏幕像素宽度：世界半宽 = (像素/2) · 每像素世界尺寸，其中每像素世界尺寸 ∝ 距离。
            float halfW = (float) (LINE_PX * 0.5 * px2World(mc) * viewLen);
            Vec3 o = perp.scale(halfW);
            Vec3[] strip = {p0.subtract(o), p0.add(o), p1.add(o), p1.subtract(o)};
            pushQuad(strip, line[0], line[1], line[2], la);
        }
    }

    /** 把一个绝对世界坐标的四边形写入 OVERLAY_BUF（QUADS/POSITION_COLOR）。 */
    private static void pushQuad(Vec3[] corners, float r, float g, float b, float a) {
        for (Vec3 p : corners) {
            OVERLAY_BUF.vertex(p.x, p.y, p.z).color(r, g, b, a).endVertex();
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
