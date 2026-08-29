package com.kalloer1.p2p.item;

import com.kalloer1.p2p.P2P;
import com.kalloer1.p2p.channel.Channel;
import com.kalloer1.p2p.channel.ChannelManager;
import com.kalloer1.p2p.channel.Role;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.gui.screens.Screen;
import com.kalloer1.p2p.network.ModMessages;
import com.kalloer1.p2p.network.WrenchUseC2SPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ChannelWrench extends Item {
    private static final String TAG_BOUND = "boundChannelId";
    private static final String TAG_MODE = "mode";
    private static final String TAG_RECENT = "recentChannels";
    private static final String TAG_SELECT = "sel";   // chain-select: stored dim + face + positions
    private static final int CHAIN_R = 8;             // half-side of the same-type scan cube
    private static final int CHAIN_CAP = 64;          // hard cap on scanned blocks per click

    public ChannelWrench(Properties properties) {
        super(properties);
    }

    // ---- NBT helpers ----
    public static int getBoundChannelId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? -1 : tag.getInt(TAG_BOUND);
    }

    public static void setBoundChannelId(ItemStack stack, int id) {
        stack.getOrCreateTag().putInt(TAG_BOUND, id);
    }

    public static Role getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || tag.getString(TAG_MODE).isEmpty()
                ? Role.INPUT
                : Role.valueOf(tag.getString(TAG_MODE));
    }

    public static void setMode(ItemStack stack, Role mode) {
        stack.getOrCreateTag().putString(TAG_MODE, mode.name());
    }

    public static void toggleMode(ItemStack stack) {
        setMode(stack, getMode(stack) == Role.INPUT ? Role.OUTPUT : Role.INPUT);
    }

    public static int[] getRecent(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_RECENT) ? new int[0] : tag.getIntArray(TAG_RECENT);
    }

    public static void pushRecent(ItemStack stack, int id) {
        List<Integer> list = new ArrayList<>();
        for (int r : getRecent(stack)) if (r != id) list.add(r);
        list.add(0, id);
        while (list.size() > 5) list.remove(list.size() - 1);
        stack.getOrCreateTag().putIntArray(TAG_RECENT,
                list.stream().mapToInt(Integer::intValue).toArray());
    }

    // ---- Multi-select (Ctrl chain-select / batch-unbind) ----
    /** Read the stored chain-select set (empty list when none). Each entry is a BlockPos in the saved dim. */
    public static List<BlockPos> getSelection(ItemStack stack) {
        List<BlockPos> out = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SELECT)) return out;
        CompoundTag sel = tag.getCompound(TAG_SELECT);
        for (long l : sel.getLongArray("pos")) out.add(BlockPos.of(l));
        return out;
    }

    /** Store the chain-select set (dim + face reference + positions) on the wrench. */
    public static void setSelection(ItemStack stack, List<BlockPos> positions, ResourceLocation dim, Direction face) {
        CompoundTag sel = new CompoundTag();
        sel.putString("dim", dim.toString());
        sel.putString("face", face.getName());
        long[] arr = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) arr[i] = positions.get(i).asLong();
        sel.putLongArray("pos", arr);
        stack.getOrCreateTag().put(TAG_SELECT, sel);
    }

    /** Clear any stored chain-select set. */
    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) tag.remove(TAG_SELECT);
    }

    // ---- Interaction ----
    /**
     * Always consume the right-click so the targeted block's own GUI never opens while the wrench is in hand.
     * The real binding / mode logic runs server-side in {@link com.kalloer1.p2p.ServerEvents} (which also
     * cancels the Forge interaction), so this method only needs to claim the click and let the packet through.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        // The wrench click is fully handled by ServerEvents.onRightClickBlock: it cancels the block interaction
        // (so containers such as chests never open their own GUI) and sends WrenchUseC2SPacket carrying the live
        // shift/control modifier state. Returning CONSUME here simply claims the click if useOn is reached another way.
        return InteractionResult.CONSUME;
    }

    /** Server-side wrench logic, invoked from WrenchUseC2SPacket (carries live shift/control state).
     *  <ul>
     *    <li>Ctrl+Shift + right-click = toggle wrench IN/OUT mode (client item NBT only).</li>
     *    <li>Ctrl + right-click = chain-select all same-type blocks; a second Ctrl press batch-unbinds them.</li>
     *    <li>Plain right-click = toggle a single faced endpoint (bind / unbind).</li>
     *  </ul> */
    public static void doWrench(ServerPlayer player, BlockPos pos, Direction face, ItemStack stack, boolean shift, boolean control) {
        Level level = player.level();

        int channelId = getBoundChannelId(stack);
        if (channelId < 0) {
            player.displayClientMessage(Component.literal("扳手未绑定频道，请在 GUI 中绑定。").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        Role mode = getMode(stack);
        P2P.LOGGER.info("[p2p] doWrench: player={} pos={} face={} shift={} ctrl={} channel={} mode={}",
                player.getName().getString(), pos, face, shift, control, channelId, mode);

        // Ctrl+Shift = toggle IN/OUT mode. Mode lives in the wrench item NBT, so we mutate a copy and re-set the
        // held stack to force an inventory sync to the client (so the tooltip updates immediately).
        if (control && shift) {
            ItemStack copy = stack.copy();
            toggleMode(copy);
            player.setItemInHand(InteractionHand.MAIN_HAND, copy);
            Role newMode = getMode(copy);
            player.displayClientMessage(Component.literal("扳手模式 → " + (newMode == Role.INPUT ? "抽取 (IN)" : "注入 (OUT)"))
                    .withStyle(newMode == Role.INPUT ? ChatFormatting.RED : ChatFormatting.BLUE), true);
            return;
        }

        Channel channel = ChannelManager.INSTANCE.getChannel(channelId);
        if (channel == null) {
            player.displayClientMessage(Component.literal("绑定的频道 #" + channelId + " 已不存在。").withStyle(ChatFormatting.RED), true);
            return;
        }
        ResourceLocation dim = level.dimension().location();

        // Ctrl only = chain bind/unbind every same-type block with the same face in radius.
        // If the clicked face is already bound, we unbind the chain; otherwise we bind it.
        if (control) {
            boolean alreadyBound = channel.members.stream()
                    .anyMatch(m -> m.pos.equals(pos) && m.face == face && m.dim.equals(dim));
            clearSelection(stack); // discard any stale two-step selection state
            if (alreadyBound) {
                ChannelManager.INSTANCE.unbindChain(level, player, pos, face, channelId, dim);
            } else {
                ChannelManager.INSTANCE.bindChain(level, player, pos, face, channelId, mode);
            }
            return;
        }

        // Plain right-click = toggle a single faced endpoint (bind / unbind).
        ChannelManager.INSTANCE.handleWrenchClick(level, player, pos, face, channelId, mode);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int id = getBoundChannelId(stack);
        Role mode = getMode(stack);
        if (id < 0) {
            tooltip.add(Component.literal("未绑定频道").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("已绑定频道 #" + id + "  [" + (mode == Role.INPUT ? "IN" : "OUT") + "]")
                    .withStyle(mode == Role.INPUT ? ChatFormatting.RED : ChatFormatting.BLUE));
        }
        tooltip.add(Component.literal("右键容器某面 = 绑定/解绑该端点").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Ctrl+Shift+右键 = 切换 抽取/注入 模式").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Ctrl+右键 = 连锁绑定/解绑周围相同方块").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
