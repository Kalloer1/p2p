package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.client.ClientChannelCache;
import com.kalloer1.p2p.channel.Network;
import com.kalloer1.p2p.network.RequestChannelsC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone "network picker" page (opened from the ChannelScreen top tab via the < > arrows' companion
 * button, or directly). Lists every network the player can see — including other players' networks when
 * the player is an admin/member of them — and jumps into ChannelScreen for the chosen one.
 * Kept separate from the channel panel so the 384×208 ceiling is never exceeded.
 */
public class NetworkScreen extends Screen {

    private static final int W = 384, H = 208;
    private static final int STONE = 0xFFC6C6C6, HI = 0xFFFFFFFF, SH = 0xFF555555;
    private static final int ROW_BG = 0xFFbdbdbd, SEL = 0xFFe8e8e8, TAB = 0xFF2f2f2f;
    private static final int INK = 0xFF2b2b2b, INK_DIM = 0xFF5a5a5a, TITLE = 0xFFe6e6e6;

    private int leftPos, topPos;
    private final List<Network> nets = new ArrayList<>();
    private final List<Integer> chanCounts = new ArrayList<>();
    private int selected = -1;
    private Button openBtn, cancelBtn;
    private final List<Hit> rows = new ArrayList<>();
    private int scroll = 0, contentH = 0;

    public NetworkScreen() { super(Component.literal("选择网络")); }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        load();
        openBtn = addRenderableWidget(Button.builder(Component.literal("进入此网络"), b -> enter()).bounds(leftPos + W - 180, topPos + H - 24, 84, 20).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("返回"), b -> minecraft.setScreen(new ChannelScreen())).bounds(leftPos + W - 92, topPos + H - 24, 84, 20).build());
        ModMessages.INSTANCE.sendToServer(new RequestChannelsC2SPacket());
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
    private CompoundTag lastData = null;

    private void drawPanel(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        gg.fill(0, 0, this.width, this.height, 0x66000000);

        // top tab
        int ty = y - 16;
        gg.fill(x, ty, x + W, ty + 16, TAB);
        gg.fill(x, ty, x + W, ty + 1, HI);
        gg.drawString(this.font, Component.literal("选择网络"), x + 6, ty + 4, TITLE, false);
        gg.drawString(this.font, Component.literal("管理员可见他人网络 · 点击进入"), x + W / 2 - 74, ty + 4, 0xFFc9c9c9, false);

        // panel + bevel
        gg.fill(x, y, x + W, y + H, STONE);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.fill(x, y, x + 1, y + H, HI);
        gg.fill(x, y + H - 1, x + W, y + H, SH);
        gg.fill(x + W - 1, y, x + W, y + H, SH);

        int listTop = y + 8, listBottom = y + H - 30, rowH = 26;
        int listH = listBottom - listTop;
        gg.fill(x + 4, listTop, x + W - 4, listBottom, ROW_BG);
        inset(gg, x + 4, listTop, W - 8, listH);

        contentH = nets.size() * rowH;
        scroll = clamp(scroll, 0, Math.max(0, contentH - listH));
        rows.clear();
        int baseY = listTop - scroll;
        for (int i = 0; i < nets.size(); i++) {
            int ry = baseY + i * rowH;
            if (ry + rowH <= listTop) continue;
            if (ry > listBottom) break;
            Network n = nets.get(i);
            boolean sel = i == selected;
            if (sel) gg.fill(x + 5, ry + 1, x + W - 5, ry + rowH - 1, SEL);
            gg.drawString(this.font, Component.literal(trim(n.name, 180)), x + 10, ry + 4, INK, false);
            String meta = "频道 " + chanCounts.get(i) + (isAdmin(n) ? "  · 管理员" : "");
            gg.drawString(this.font, Component.literal(meta), x + 10, ry + 16, INK_DIM, false);
            rows.add(new Hit(x + 5, ry + 1, W - 10, rowH - 1, i));
        }
        if (nets.isEmpty())
            gg.drawString(this.font, Component.literal("没有可访问的网络"), x + 12, listTop + 12, INK_DIM, false);
    }

    private boolean isAdmin(Network n) {
        // local viewer cannot be resolved here cheaply; show "管理员" only when admins set is non-empty
        return !n.admins.isEmpty();
    }

    private void enter() {
        if (selected < 0 || selected >= nets.size()) return;
        ClientChannelCache.INSTANCE.setCurrentNetworkId(nets.get(selected).id);
        minecraft.setScreen(new ChannelScreen());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int ix = (int) mx, iy = (int) my;
        for (Hit h : rows) {
            if (ix >= h.x && ix <= h.x + h.w && iy >= h.y && iy <= h.y + h.h) { selected = h.id; return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int listTop = topPos + 8, listBottom = topPos + H - 30;
        if (mx >= leftPos + 4 && mx <= leftPos + W - 4 && my >= listTop && my <= listBottom)
            scroll = clamp(scroll - (int) delta * 26, 0, Math.max(0, contentH - (listBottom - listTop)));
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
    private void inset(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + 1, SH);
        gg.fill(x, y, x + 1, y + h, SH);
        gg.fill(x, y + h - 1, x + w, y + h, HI);
        gg.fill(x + w - 1, y, x + w, y + h, HI);
    }
    private record Hit(int x, int y, int w, int h, int id) {}
}
