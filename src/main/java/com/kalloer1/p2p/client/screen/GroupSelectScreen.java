package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.channel.Group;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple list picker for assigning a channel to a group. Shows every group in the current network plus
 * a "(无分组)" option at the top. Selecting an entry sends SET_GROUP and returns to the parent screen.
 */
public class GroupSelectScreen extends Screen {

    private static final int W = 200, H = 176;
    private static final int STONE = 0xFFC6C6C6, HI = 0xFFFFFFFF, SH = 0xFF555555;
    private static final int ROW_BG = 0xFFbdbdbd, SEL = 0xFFe8e8e8, TAB = 0xFF2f2f2f;
    private static final int INK = 0xFF2b2b2b, INK_DIM = 0xFF5a5a5a, TITLE = 0xFFe6e6e6;

    private final Screen parent;
    private final int channelId;
    private final int currentGroupId;
    private final List<GroupRow> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    private int leftPos, topPos;
    private int scroll = 0, contentH = 0;
    private Button okBtn;
    private int selected = -1;

    public GroupSelectScreen(Screen parent, int channelId, int currentGroupId, List<Group> groups) {
        super(Component.literal("选择分组"));
        this.parent = parent;
        this.channelId = channelId;
        this.currentGroupId = currentGroupId;
        rows.add(new GroupRow(-1, "(无分组)"));
        for (Group g : groups) rows.add(new GroupRow(g.id, g.name));
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id == currentGroupId) { selected = i; break; }
        if (selected < 0) selected = 0;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        okBtn = addRenderableWidget(Button.builder(Component.literal("确定"), b -> confirm())
                .bounds(leftPos + W - 92, topPos + H - 24, 84, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent))
                .bounds(leftPos + 8, topPos + H - 24, 84, 20).build());
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawPanel(gg);
        super.render(gg, mx, my, pt);
    }

    private void drawPanel(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        gg.fill(0, 0, this.width, this.height, 0x66000000);

        // top tab
        int ty = y - 16;
        gg.fill(x, ty, x + W, ty + 16, TAB);
        gg.fill(x, ty, x + W, ty + 1, HI);
        gg.drawString(this.font, Component.literal("选择分组"), x + 6, ty + 4, TITLE, false);

        // panel + bevel
        gg.fill(x, y, x + W, y + H, STONE);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.fill(x, y, x + 1, y + H, HI);
        gg.fill(x, y + H - 1, x + W, y + H, SH);
        gg.fill(x + W - 1, y, x + W, y + H, SH);

        int listTop = y + 8, listBottom = y + H - 30, rowH = 18;
        int listH = listBottom - listTop;
        gg.fill(x + 4, listTop, x + W - 4, listBottom, ROW_BG);
        inset(gg, x + 4, listTop, W - 8, listH);

        contentH = rows.size() * rowH;
        scroll = clamp(scroll, 0, Math.max(0, contentH - listH));
        hits.clear();
        int baseY = listTop - scroll;
        for (int i = 0; i < rows.size(); i++) {
            int ry = baseY + i * rowH;
            if (ry + rowH <= listTop) continue;
            if (ry > listBottom) break;
            GroupRow r = rows.get(i);
            boolean sel = i == selected;
            boolean current = r.id == currentGroupId;
            if (sel) gg.fill(x + 5, ry + 1, x + W - 5, ry + rowH - 1, SEL);
            String label = (current ? "✓ " : "  ") + r.name;
            gg.drawString(this.font, Component.literal(label), x + 10, ry + 5, current ? 0xFF2f8a2f : INK, false);
            hits.add(new Hit(x + 5, ry + 1, W - 10, rowH - 1, i));
        }
        if (rows.isEmpty())
            gg.drawString(this.font, Component.literal("没有可选分组"), x + 12, listTop + 12, INK_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int ix = (int) mx, iy = (int) my;
        for (Hit h : hits) {
            if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) {
                selected = h.id;
                confirm();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int listTop = topPos + 8, listBottom = topPos + H - 30;
        if (mx >= leftPos + 4 && mx <= leftPos + W - 4 && my >= listTop && my <= listBottom)
            scroll = clamp(scroll - (int) delta * 18, 0, Math.max(0, contentH - (listBottom - listTop)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void confirm() {
        if (selected >= 0 && selected < rows.size()) {
            int gid = rows.get(selected).id;
            if (gid != currentGroupId)
                ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(channelId,
                        ChannelActionC2SPacket.Action.SET_GROUP, null, gid, null, false, null, ""));
        }
        minecraft.setScreen(parent);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void inset(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + 1, SH);
        gg.fill(x, y, x + 1, y + h, SH);
        gg.fill(x, y + h - 1, x + w, y + h, HI);
        gg.fill(x + w - 1, y, x + w, y + h, HI);
    }

    private record GroupRow(int id, String name) {}
    private record Hit(int x, int y, int w, int h, int id) {}
}
