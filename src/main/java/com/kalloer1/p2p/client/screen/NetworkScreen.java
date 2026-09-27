package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.channel.Network;
import com.kalloer1.p2p.client.ClientChannelCache;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone "network picker" page (opened from the ChannelScreen navigation row's network pill).
 * Lists every network the player can see — including other players' networks when the player is an
 * admin/member of them — and jumps into ChannelScreen for the chosen one.
 *
 * <p>1.2.0 skin: masked dialog + rounded white card, two-line iOS list rows (name + role/count footnote).
 */
public class NetworkScreen extends Screen {

    private static final int W = 384, H = 216;
    private static final int ROW_H = 30;
    private static final int LIST_TOP = 30;
    private static final int BTN_Y = H - 26;
    private static final int LIST_BOTTOM = BTN_Y - 6;

    private int leftPos, topPos;
    private final List<Network> nets = new ArrayList<>();
    private final List<Integer> chanCounts = new ArrayList<>();
    private int selected = -1;
    private Button openBtn, cancelBtn;
    private final List<Hit> rows = new ArrayList<>();
    private int scroll = 0, contentH = 0;
    private CompoundTag lastData = null;

    public NetworkScreen() { super(Lang.t(Lang.UI_SELECT_NETWORK)); }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        load();
        openBtn = textButton(Lang.t(Lang.UI_ENTER_NETWORK), b -> enter(), true);
        cancelBtn = textButton(Lang.t(Lang.UI_BACK), b -> minecraft.setScreen(new ChannelScreen()), false);
        PacketDistributor.sendToServer(new RequestChannelsC2SPacket());
    }

    private Button textButton(Component label, Button.OnPress action, boolean trailingRight) {
        int w = this.font.width(label) + 12;
        int x = trailingRight ? leftPos + W - P2PTheme.PAD - w : leftPos + P2PTheme.PAD;
        P2PFlatButton b = new P2PFlatButton(Button.builder(label, action).bounds(x, topPos + BTN_Y, w, 14));
        b.setStyle(P2PFlatButton.Style.TEXT);
        return addRenderableWidget(b);
    }

    private void load() {
        nets.clear();
        chanCounts.clear();
        selected = -1;
        CompoundTag data = ClientChannelCache.INSTANCE.getData();
        if (data == null) return;
        ListTag netList = data.getList("networks", Tag.TAG_COMPOUND);
        for (Tag t : netList) nets.add(Network.deserializeNBT((CompoundTag) t));
        ListTag chList = data.getList("channels", Tag.TAG_COMPOUND);
        for (Network n : nets) {
            int cnt = 0;
            for (Tag t : chList) if (((CompoundTag) t).getInt("networkId") == n.id) cnt++;
            chanCounts.add(cnt);
        }
        if (!nets.isEmpty()) selected = 0;
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        CompoundTag data = ClientChannelCache.INSTANCE.getData();
        if (data != null && data != lastData) { lastData = data; load(); }
        drawPanel(gg);
        super.render(gg, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mx, int my, float pt) {
        // no-op: drawPanel() already covers the screen
    }

    private void drawPanel(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        P2PTheme.dialog(gg, this.width, this.height, x, y, W, H, this.font, Lang.t(Lang.UI_SELECT_NETWORK));
        P2PTheme.drawCentered(gg, this.font, Lang.t(Lang.UI_SHARE_HINT), x, y + 19, W, P2PTheme.TEXT_TERT);

        int listTop = y + LIST_TOP, listBottom = y + LIST_BOTTOM;
        int listH = listBottom - listTop;

        contentH = nets.size() * ROW_H;
        scroll = clamp(scroll, 0, Math.max(0, contentH - listH));
        rows.clear();
        gg.enableScissor(x + 1, listTop, x + W - 1, listBottom);
        int baseY = listTop - scroll;
        for (int i = 0; i < nets.size(); i++) {
            int ry = baseY + i * ROW_H;
            if (ry + ROW_H <= listTop) continue;
            if (ry > listBottom) break;
            Network n = nets.get(i);
            if (i == selected) gg.fill(x + 1, ry, x + W - 1, ry + ROW_H, P2PTheme.CARD_SEL);
            gg.drawString(this.font, Component.literal(trim(n.name, W - 64)), x + P2PTheme.PAD, ry + 5,
                    P2PTheme.TEXT_PRIMARY, false);
            String role = roleOf(n);
            String meta = Lang.t(Lang.UI_CHANNEL_COUNT, chanCounts.get(i)).getString() + (role.isEmpty() ? "" : "  ·  " + role);
            gg.drawString(this.font, Component.literal(meta), x + P2PTheme.PAD, ry + 17, P2PTheme.TEXT_SECOND, false);
            rows.add(new Hit(x + 1, ry, W - 2, ROW_H, i));
            gg.fill(x + P2PTheme.PAD, ry + ROW_H - 1, x + W - P2PTheme.PAD, ry + ROW_H, P2PTheme.SEPARATOR);
        }
        gg.disableScissor();
        if (nets.isEmpty())
            gg.drawString(this.font, Lang.t(Lang.UI_NO_NETWORK), x + P2PTheme.PAD, listTop + 12, P2PTheme.TEXT_SECOND, false);
    }

    /** 本机玩家在该网络里的身份：拥有者 / 管理员 / 共享（被邀请加入）。 */
    private String roleOf(Network n) {
        if (this.minecraft == null || this.minecraft.player == null) return "";
        String me = this.minecraft.player.getStringUUID();
        if (n.owner != null && n.owner.equals(me)) return Lang.t(Lang.UI_ROLE_OWNER).getString();
        if (n.admins.contains(me)) return Lang.t(Lang.UI_ROLE_ADMIN).getString();
        if (n.members.contains(me)) return Lang.t(Lang.UI_ROLE_SHARED).getString();
        return "";
    }

    private void enter() {
        if (selected < 0 || selected >= nets.size()) return;
        ClientChannelCache.INSTANCE.setCurrentNetworkId(nets.get(selected).id);
        minecraft.setScreen(new ChannelScreen());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int ix = (int) mx, iy = (int) my;
        for (Hit h : rows) {
            if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) { selected = h.id; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int listTop = topPos + LIST_TOP, listBottom = topPos + LIST_BOTTOM;
        if (mx >= leftPos + 1 && mx <= leftPos + W - 1 && my >= listTop && my <= listBottom)
            scroll = clamp(scroll - (int) scrollY * ROW_H, 0, Math.max(0, contentH - (listBottom - listTop)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(new ChannelScreen()); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private String trim(String s, int maxPx) {
        if (s == null) return "";
        if (this.font.width(s) <= maxPx) return s;
        while (this.font.width(s + "…") > maxPx && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private record Hit(int x, int y, int w, int h, int id) {}
}
