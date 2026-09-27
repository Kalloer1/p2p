package com.kalloer1.p2p.client.screen;

import com.kalloer1.p2p.Lang;
import com.kalloer1.p2p.network.ChannelActionC2SPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Reusable single-line text prompt overlay.
 * action=0: rename a channel (channelId); action=1: create a group;
 * action=2: set a member's routing priority (memberKey selects the endpoint, value must be an integer).
 *
 * <p>1.2.0 skin: masked dialog + rounded white card (spec section 3, last row); the field is a
 * borderless EditBox drawn over a FIELD_BG pill so no vanilla widget texture shows through.
 */
public class NameInputScreen extends Screen {
    private static final int W = 216, H = 92;
    private static final int FIELD_Y = 30, FIELD_H = 18;
    private static final int BTN_Y = H - 24;

    private final Screen parent;
    private final Component title;
    private final String initial;
    private final int channelId;
    private final int action;
    private final String memberKey;
    private int leftPos, topPos;
    private EditBox box;
    private Button okBtn, cancelBtn;

    public NameInputScreen(Screen parent, Component title, String initial, int action, int channelId, String memberKey) {
        super(title);
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
        box = new EditBox(this.font, leftPos + P2PTheme.PAD + 4, topPos + FIELD_Y + 3, W - P2PTheme.PAD * 2 - 8, 12,
                Lang.t(Lang.UI_INPUT_HINT));
        if (action == 2) {
            box.setMaxLength(10);  // fits Integer.MIN_VALUE..Integer.MAX_VALUE
            box.setFilter(s -> s.matches("-?[0-9]*"));
        } else {
            box.setMaxLength(24);
        }
        box.setBordered(false);
        box.setTextColor(P2PTheme.TEXT_PRIMARY);
        box.setHint(Lang.t(Lang.UI_INPUT_HINT));
        box.setValue(initial);
        box.setFocused(true);
        addRenderableWidget(box);
        okBtn = textButton(Lang.t(Lang.UI_OK), b -> confirm(), true);
        cancelBtn = textButton(Lang.t(Lang.UI_CANCEL), b -> minecraft.setScreen(parent), false);
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
        super.render(gg, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mx, int my, float pt) {
        P2PTheme.dialog(gg, this.width, this.height, leftPos, topPos, W, H, this.font, title);
        P2PTheme.field(gg, leftPos + P2PTheme.PAD, topPos + FIELD_Y, W - P2PTheme.PAD * 2, FIELD_H);
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
            PacketDistributor.sendToServer(new ChannelActionC2SPacket(channelId, ChannelActionC2SPacket.Action.SET_PRIORITY,
                    null, prio, memberKey, false, null, ""));
        } else if (action == 1) {
            PacketDistributor.sendToServer(new ChannelActionC2SPacket(-1, ChannelActionC2SPacket.Action.CREATE_GROUP, null, 0, null, false, null, v));
        } else {
            PacketDistributor.sendToServer(new ChannelActionC2SPacket(channelId, ChannelActionC2SPacket.Action.RENAME, null, 0, null, false, null, v));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_ESCAPE) { minecraft.setScreen(parent); return true; }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { confirm(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
