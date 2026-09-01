package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Small overlay to pick the channel type when creating one. Type is locked at creation,
 * so this is the only place the player chooses ITEM / FLUID / ENERGY.
 */
public class ChannelCreateScreen extends Screen {
    private static final int W = 384, H = 132;
    private static final int STONE = 0xFFC6C6C6, HI = 0xFFFFFFFF, SH = 0xFF555555, TAB = 0xFF2f2f2f;
    private static final int INK = 0xFF2b2b2b, TITLE = 0xFFe6e6e6;
    private final Screen parent;
    private int leftPos, topPos;
    private Button itemBtn, fluidBtn, energyBtn, cancelBtn;

    public ChannelCreateScreen(Screen parent) {
        super(Lang.t(Lang.UI_NEW_CHANNEL));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        // three normal-sized type buttons in a row (104×22, not the oversized 112×44)
        int btnW = 104, btnH = 22, gap = 14;
        int total = btnW * 3 + gap * 2;
        int rx = leftPos + (W - total) / 2;
        int by = topPos + 54;
        itemBtn = addRenderableWidget(Button.builder(Lang.t(Lang.UI_TYPE_ITEM), b -> create("ITEM")).bounds(rx, by, btnW, btnH).build());
        fluidBtn = addRenderableWidget(Button.builder(Lang.t(Lang.UI_TYPE_FLUID), b -> create("FLUID")).bounds(rx + btnW + gap, by, btnW, btnH).build());
        energyBtn = addRenderableWidget(Button.builder(Lang.t(Lang.UI_TYPE_ENERGY), b -> create("ENERGY")).bounds(rx + 2 * (btnW + gap), by, btnW, btnH).build());
        cancelBtn = addRenderableWidget(Button.builder(Lang.t(Lang.UI_CANCEL), b -> minecraft.setScreen(parent)).bounds(leftPos + W - 96, topPos + H - 26, 88, 20).build());
    }

    private void create(String type) {
        ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(-1, ChannelActionC2SPacket.Action.CREATE, type, -1, null, false, null, ""));
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawPanel(gg);
        super.render(gg, mx, my, pt);
    }

    private void drawPanel(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        gg.fill(0, 0, this.width, this.height, 0x66000000);
        // panel + bevel
        gg.fill(x, y, x + W, y + H, STONE);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.fill(x, y, x + 1, y + H, HI);
        gg.fill(x, y + H - 1, x + W, y + H, SH);
        gg.fill(x + W - 1, y, x + W, y + H, SH);
        // header strip
        gg.fill(x, y, x + W, y + 17, TAB);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.drawString(this.font, Lang.t(Lang.UI_CREATE_HEADER), x + 6, y + 5, TITLE, false);
        // type icons removed (reverted to plain stone UI)
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
