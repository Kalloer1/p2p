package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
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
 * Simple list picker for assigning channel(s) to a group. Shows every group in the current network plus
 * a "(无分组)" option at the top. Selecting an entry sends SET_GROUP and returns to the parent screen.
 *
 * <p>1.2.0 skin: masked dialog + rounded white card, rows are flush iOS list rows (18px, separators
 * inset 8) and the destructive/confirm pair are text buttons.
 *
 * <p>1.4.0 批量化：调用方传入一组频道 id（多选）或单频道；选「（无分组）」= 对全部目标频道
 * 发 SET_GROUP(-1)（即批量移出组）。逐频道复用既有 SET_GROUP 动作，不新增 payload。
 */
public class GroupSelectScreen extends Screen {

    private static final int W = 216, H = 184;
    private static final int LIST_TOP = 26;
    private static final int BTN_Y = H - 24;

    private final Screen parent;
    private final List<Integer> channelIds;
    private final int currentGroupId;
    private final List<GroupRow> rows = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    private int leftPos, topPos;
    private int scroll = 0, contentH = 0;
    private Button okBtn;
    private int selected = -1;

    /** 兼容单频道的旧入口。 */
    public GroupSelectScreen(Screen parent, int channelId, int currentGroupId, List<Group> groups) {
        this(parent, List.of(channelId), currentGroupId, groups);
    }

    /** 批量入口：{@code channelIds} 为应用目标频道集合（客户端 ChannelScreen 多选结果）。 */
    public GroupSelectScreen(Screen parent, List<Integer> channelIds, int currentGroupId, List<Group> groups) {
        super(Lang.t(Lang.UI_SELECT_GROUP));
        this.parent = parent;
        this.channelIds = channelIds;
        this.currentGroupId = currentGroupId;
        rows.add(new GroupRow(-1, Lang.t(Lang.UI_GROUP_NONE_OPTION).getString()));
        for (Group g : groups) rows.add(new GroupRow(g.id, g.name));
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id == currentGroupId) { selected = i; break; }
        if (selected < 0) selected = 0;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        okBtn = textButton(Lang.t(Lang.UI_OK), b -> confirm(), true);
        addRenderableWidget(textButton(Lang.t(Lang.UI_CANCEL), b -> minecraft.setScreen(parent), false));
    }

    private Button textButton(Component label, Button.OnPress action, boolean trailingRight) {
        int w = this.font.width(label) + 12;
        int x = trailingRight ? leftPos + W - P2PTheme.PAD - w : leftPos + P2PTheme.PAD;
        P2PFlatButton b = new P2PFlatButton(Button.builder(label, action).bounds(x, topPos + BTN_Y, w, 14));
        b.setStyle(P2PFlatButton.Style.TEXT);
        return addRenderableWidget(b);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawPanel(gg);
        super.render(gg, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics gg) {
        // no-op: drawPanel() already covers the screen
    }

    private void drawPanel(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        P2PTheme.dialog(gg, this.width, this.height, x, y, W, H, this.font, Lang.t(Lang.UI_SELECT_GROUP));

        int listTop = y + LIST_TOP, listBottom = y + BTN_Y - 8, rowH = P2PTheme.ROW_H;
        int listH = listBottom - listTop;

        contentH = rows.size() * rowH;
        scroll = clamp(scroll, 0, Math.max(0, contentH - listH));
        hits.clear();
        gg.enableScissor(x + 1, listTop, x + W - 1, listBottom);
        int baseY = listTop - scroll;
        for (int i = 0; i < rows.size(); i++) {
            int ry = baseY + i * rowH;
            if (ry + rowH <= listTop) continue;
            if (ry > listBottom) break;
            GroupRow r = rows.get(i);
            boolean sel = i == selected;
            boolean current = r.id == currentGroupId;
            if (sel) gg.fill(x + 1, ry, x + W - 1, ry + rowH, P2PTheme.CARD_SEL);
            String label = (current ? "✓ " : "  ") + r.name;
            gg.drawString(this.font, Component.literal(label), x + P2PTheme.PAD, ry + 5,
                    current ? P2PTheme.SUCCESS : P2PTheme.TEXT_PRIMARY, false);
            hits.add(new Hit(x + 1, ry, W - 2, rowH, i));
            gg.fill(x + P2PTheme.PAD, ry + rowH - 1, x + W - P2PTheme.PAD, ry + rowH, P2PTheme.SEPARATOR);
        }
        gg.disableScissor();
        if (rows.isEmpty())
            gg.drawString(this.font, Lang.t(Lang.UI_NO_GROUP), x + P2PTheme.PAD, listTop + 12, P2PTheme.TEXT_SECOND, false);
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
        int listTop = topPos + LIST_TOP, listBottom = topPos + BTN_Y - 8;
        if (mx >= leftPos + 1 && mx <= leftPos + W - 1 && my >= listTop && my <= listBottom)
            scroll = clamp(scroll - (int) delta * P2PTheme.ROW_H, 0, Math.max(0, contentH - (listBottom - listTop)));
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
            // 1.4.0：逐频道发既有 SET_GROUP；gid=-1（无分组）即批量移出组。
            // 不按 currentGroupId 过滤（多选时各频道原组可能不同），统一落位。
            for (int cid : channelIds)
                ModMessages.sendToServer(new ChannelActionC2SPacket(cid,
                        ChannelActionC2SPacket.Action.SET_GROUP, null, gid, null, false, null, ""));
        }
        minecraft.setScreen(parent);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private record GroupRow(int id, String name) {}
    private record Hit(int x, int y, int w, int h, int id) {}
}
