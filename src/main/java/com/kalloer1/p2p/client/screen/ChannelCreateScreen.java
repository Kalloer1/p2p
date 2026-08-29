package com.kalloer1.p2p.client.screen;

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
        super(Component.literal("新建频道"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        int rx = leftPos + 12;
        itemBtn = addRenderableWidget(Button.builder(Component.literal("物品"), b -> create("ITEM")).bounds(rx, topPos + 48, 112, 44).build());
        fluidBtn = addRenderableWidget(Button.builder(Component.literal("流体"), b -> create("FLUID")).bounds(rx + 124, topPos + 48, 112, 44).build());
        energyBtn = addRenderableWidget(Button.builder(Component.literal("能量"), b -> create("ENERGY")).bounds(rx + 248, topPos + 48, 112, 44).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent)).bounds(leftPos + W - 96, topPos + H - 26, 88, 20).build());
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
        gg.drawString(this.font, Component.literal("新建频道 · 选择类型"), x + 6, y + 5, TITLE, false);
        // type icons removed (reverted to plain stone UI)
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
