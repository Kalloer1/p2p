package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.kalloer1.p2p.network.ModMessages;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Reusable single-line text prompt overlay.
 * action=0: rename a channel (channelId); action=1: create a group;
 * action=2: set a member's routing priority (memberKey selects the endpoint, value must be an integer).
 */
public class NameInputScreen extends Screen {
    private static final int W = 384, H = 120;
    private static final int STONE = 0xFFC6C6C6, HI = 0xFFFFFFFF, SH = 0xFF555555, TAB = 0xFF2f2f2f;
    private static final int INK = 0xFF2b2b2b, TITLE = 0xFFe6e6e6;
    private final Screen parent;
    private final String title;
    private final String initial;
    private final int channelId;
    private final int action;
    private final String memberKey;
    private int leftPos, topPos;
    private EditBox box;
    private Button okBtn, cancelBtn;

    public NameInputScreen(Screen parent, String title, String initial, int action, int channelId, String memberKey) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.initial = initial == null ? "" : initial;
        this.channelId = channelId;
        this.action = action;
        this.memberKey = memberKey;
    }

    @Override
    protected void init() {
        leftPos = (this.width - W) / 2;
        topPos = (this.height - H) / 2;
        box = new EditBox(this.font, leftPos + 12, topPos + 44, W - 24, 16, Component.literal("输入"));
        if (action == 2) {
            box.setMaxLength(10);  // fits Integer.MIN_VALUE..Integer.MAX_VALUE
            box.setFilter(s -> s.matches("-?[0-9]*"));
        } else {
            box.setMaxLength(24);
        }
        box.setValue(initial);
        box.setFocused(true);
        addRenderableWidget(box);
        okBtn = addRenderableWidget(Button.builder(Component.literal("确定"), b -> confirm()).bounds(leftPos + W - 184, topPos + H - 26, 84, 20).build());
        cancelBtn = addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent)).bounds(leftPos + W - 92, topPos + H - 26, 84, 20).build());
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        super.render(gg, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics gg) {
        int x = leftPos, y = topPos;
        gg.fill(0, 0, this.width, this.height, 0x66000000);
        gg.fill(x, y, x + W, y + H, STONE);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.fill(x, y, x + 1, y + H, HI);
        gg.fill(x, y + H - 1, x + W, y + H, SH);
        gg.fill(x + W - 1, y, x + W, y + H, SH);
        // header strip
        gg.fill(x, y, x + W, y + 17, TAB);
        gg.fill(x, y, x + W, y + 1, HI);
        gg.drawString(this.font, Component.literal(title), x + 6, y + 5, TITLE, false);
    }

    private void confirm() {
        String v = box.getValue().trim();
        if (v.isEmpty()) return;
        if (action == 2) {
            int prio;
            try {
                prio = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return;
            }
            ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(channelId, ChannelActionC2SPacket.Action.SET_PRIORITY,
                    null, prio, memberKey, false, null, ""));
        } else if (action == 1) {
            ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(-1, ChannelActionC2SPacket.Action.CREATE_GROUP, null, 0, null, false, null, v));
        } else {
            ModMessages.INSTANCE.sendToServer(new ChannelActionC2SPacket(channelId, ChannelActionC2SPacket.Action.RENAME, null, 0, null, false, null, v));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
