package com.kalloer1.p2p;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Centralised translation keys for every user-facing string in the mod.
 *
 * Both {@code en_us.json} (English fallback) and {@code zh_cn.json} (Chinese, the mod author's locale)
 * ship the same key set, so Minecraft picks the right language automatically. Call sites use
 * {@link #t(String, Object...)} to build a {@link Component#translatable(String, Object...)}.
 */
public final class Lang {
    private Lang() {}

    public static MutableComponent t(String key, Object... args) {
        return Component.translatable(key, args);
    }

    // ---- ChannelScreen generic UI ----
    public static final String UI_TITLE = "p2p.ui.title";
    public static final String UI_SEARCH = "p2p.ui.search";
    public static final String UI_REDSTONE = "p2p.ui.redstone";
    public static final String UI_DISTRIBUTION = "p2p.ui.distribution";
    public static final String UI_GROUP = "p2p.ui.group";
    public static final String UI_TYPE_UNKNOWN = "p2p.type.unknown";
    public static final String UI_CHUNKLOAD_ON = "p2p.ui.chunkload_on";
    public static final String UI_CHUNKLOAD_OFF = "p2p.ui.chunkload_off";
    public static final String UI_RATE_MAX = "p2p.ui.rate_max";
    public static final String UI_BIND_CHANNEL = "p2p.ui.bind_channel";
    public static final String UI_DELETE_CHANNEL = "p2p.ui.delete_channel";
    public static final String UI_NEW_CHANNEL = "p2p.ui.new_channel";
    public static final String UI_NEW_GROUP = "p2p.ui.new_group";
    public static final String UI_DELETE_GROUP = "p2p.ui.delete_group";
    public static final String UI_MEDIUM = "p2p.ui.medium";
    public static final String UI_DIM = "p2p.ui.dim";
    public static final String UI_POS = "p2p.ui.pos";
    public static final String UI_FACE = "p2p.ui.face";
    public static final String UI_FLOW = "p2p.ui.flow";
    public static final String UI_MEMBER_COUNT = "p2p.ui.member_count";
    public static final String UI_NO_CHANNEL = "p2p.ui.no_channel";
    public static final String UI_NO_CHANNEL_HINT1 = "p2p.ui.no_channel_hint1";
    public static final String UI_NO_CHANNEL_HINT2 = "p2p.ui.no_channel_hint2";
    public static final String UI_RATE = "p2p.ui.rate";
    public static final String UI_THROTTLE = "p2p.ui.throttle";
    public static final String UI_EVERY_TICKS = "p2p.ui.every_ticks";
    public static final String UI_NO_ENDPOINT = "p2p.ui.no_endpoint";
    public static final String UI_FILTER = "p2p.ui.filter";
    public static final String UI_GROUP_NONE = "p2p.ui.group_none";
    public static final String UI_NO_MATCH = "p2p.ui.no_match";

    // ---- 配置 pill 短标签（spec v1.1：pill 只显短词，值进 tooltip，非默认态 TINT 提亮） ----
    public static final String UI_PILL_REDSTONE = "p2p.ui.pill.redstone";
    public static final String UI_PILL_DIST = "p2p.ui.pill.dist";
    public static final String UI_PILL_GROUP = "p2p.ui.pill.group";
    public static final String UI_PILL_CLEAN = "p2p.ui.pill.clean";
    public static final String UI_PILL_CHUNKLOAD = "p2p.ui.pill.chunkload";
    /** 面板脚注：端点多选 + 复制/粘贴提示。 */
    public static final String UI_ENDPOINT_HINT = "p2p.ui.endpoint_hint";
    /** 面板脚注（悬停左列表时）：频道多选 + 双击重命名说明（1.4.0）。 */
    public static final String UI_CHANNEL_HINT = "p2p.ui.channel_hint";

    // ---- 端点行失效状态（1.3.1：面板实时解析世界现状，不做破坏自动移除） ----
    /** 已失效（维度已加载、坐标处方块为空气）行内标注。 */
    public static final String UI_ENDPOINT_INVALID = "p2p.ui.endpoint_invalid";
    /** 已失效行的悬停说明。 */
    public static final String UI_ENDPOINT_INVALID_TIP = "p2p.ui.endpoint_invalid_tip";
    /** 维度未加载（状态未知）行内提示。 */
    public static final String UI_ENDPOINT_UNLOADED = "p2p.ui.endpoint_unloaded";
    /** 清理失效端点按钮 tooltip。 */
    public static final String UI_CLEAN_INVALID = "p2p.ui.clean_invalid";

    // ---- channel type ----
    public static final String UI_TYPE_ITEM = "p2p.ui.type.item";
    public static final String UI_TYPE_FLUID = "p2p.ui.type.fluid";
    public static final String UI_TYPE_ENERGY = "p2p.ui.type.energy";

    // ---- distribution mode ----
    public static final String UI_DIST_PRIORITY = "p2p.ui.dist.priority";
    public static final String UI_DIST_NEAREST = "p2p.ui.dist.nearest";
    public static final String UI_DIST_FARTHEST = "p2p.ui.dist.farthest";
    public static final String UI_DIST_SPLIT = "p2p.ui.dist.split";
    public static final String UI_DIST_ROUND = "p2p.ui.dist.round";

    // ---- redstone mode ----
    public static final String UI_RED_HIGH = "p2p.ui.redstone.high";
    public static final String UI_RED_LOW = "p2p.ui.redstone.low";
    public static final String UI_RED_NEVER = "p2p.ui.redstone.never";
    public static final String UI_RED_IGNORE = "p2p.ui.redstone.ignore";

    // ---- direction (face) ----
    public static final String UI_FACE_UP = "p2p.ui.face.up";
    public static final String UI_FACE_DOWN = "p2p.ui.face.down";
    public static final String UI_FACE_NORTH = "p2p.ui.face.north";
    public static final String UI_FACE_SOUTH = "p2p.ui.face.south";
    public static final String UI_FACE_EAST = "p2p.ui.face.east";
    public static final String UI_FACE_WEST = "p2p.ui.face.west";

    // ---- dimension short labels ----
    public static final String UI_DIM_OVERWORLD = "p2p.ui.dim.overworld";
    public static final String UI_DIM_NETHER = "p2p.ui.dim.nether";
    public static final String UI_DIM_END = "p2p.ui.dim.end";

    // ---- unit labels ----
    /**
     * 介质单位键前缀：{@code p2p.unit.<介质id，非字母数字换成下划线>}，如 {@code p2p.unit.create_stress}。
     * 单位与介质 displayName 分开（旧 {@code p2p.ui.unit.*} 三键合并到此），
     * 速率行因此只需并排显示「名称 + 单位」而不再截断长名称。
     */
    public static final String UNIT_PREFIX = "p2p.unit.";

    /** 介质 id → 单位翻译键。 */
    public static String unitKey(String mediumId) {
        return UNIT_PREFIX + (mediumId == null ? "" : mediumId.replaceAll("[^A-Za-z0-9]", "_"));
    }

    /** 单位文案；该介质没有配键时返回空串（调用方只显示名称）。 */
    public static String unitOf(String mediumId) {
        String key = unitKey(mediumId);
        String value = Component.translatable(key).getString();
        return value.equals(key) ? "" : value;
    }

    // ---- network role ----
    public static final String UI_ROLE_OWNER = "p2p.ui.role.owner";
    public static final String UI_ROLE_ADMIN = "p2p.ui.role.admin";
    public static final String UI_ROLE_SHARED = "p2p.ui.role.shared";

    // ---- FilterEditor ----
    public static final String UI_FILTER_TITLE = "p2p.ui.filter.title";
    public static final String UI_CANCEL = "p2p.ui.cancel";
    public static final String UI_DONE = "p2p.ui.done";
    public static final String UI_TAB_ITEM = "p2p.ui.tab.item";
    public static final String UI_TAB_TAG = "p2p.ui.tab.tag";
    public static final String UI_TAB_NBT = "p2p.ui.tab.nbt";
    public static final String UI_MODE = "p2p.ui.mode";
    public static final String UI_MODE_WHITELIST = "p2p.ui.mode.whitelist";
    public static final String UI_MODE_BLACKLIST = "p2p.ui.mode.blacklist";
    public static final String UI_NBT_SWITCH = "p2p.ui.nbt_switch";
    public static final String UI_NBT_STRICT = "p2p.ui.nbt.strict";
    public static final String UI_NBT_IGNORE = "p2p.ui.nbt.ignore";
    public static final String UI_TAG_HINT = "p2p.ui.tag_hint";
    public static final String UI_TAG_PLACEHOLDER = "p2p.ui.tag_placeholder";
    public static final String UI_ADD_TAG = "p2p.ui.add_tag";
    public static final String UI_TAG_TIP = "p2p.ui.tag_tip";
    public static final String UI_NBT_REGEX = "p2p.ui.nbt_regex";
    public static final String UI_NBT_REGEX_HINT = "p2p.ui.nbt_regex_hint";
    public static final String UI_ENERGY_NOTE = "p2p.ui.energy_note";
    public static final String UI_TAG_FLUID_HINT = "p2p.ui.tag_fluid_hint";
    public static final String UI_NBT_ITEM_HINT = "p2p.ui.nbt_item_hint";
    public static final String UI_NBT_FLUID_HINT = "p2p.ui.nbt_fluid_hint";
    public static final String UI_ITEM_HINT = "p2p.ui.item_hint";
    public static final String UI_FLUID_HINT = "p2p.ui.fluid_hint";
    public static final String UI_TAG_EMPTY = "p2p.ui.tag_empty";
    public static final String UI_FILTER_TYPE_ONLY = "p2p.ui.filter_type_only";
    public static final String UI_FILTER_TYPE_ONLY2 = "p2p.ui.filter_type_only2";

    // ---- ChannelCreateScreen ----
    public static final String UI_CREATE_HEADER = "p2p.ui.create.header";

    // ---- NameInputScreen titles ----
    public static final String UI_RENAME = "p2p.ui.rename";
    public static final String UI_SET_PRIORITY = "p2p.ui.set_priority";

    // ---- NameInputScreen ----
    public static final String UI_INPUT_HINT = "p2p.ui.input_hint";
    public static final String UI_OK = "p2p.ui.ok";

    // ---- GroupSelectScreen ----
    public static final String UI_SELECT_GROUP = "p2p.ui.select_group";
    public static final String UI_GROUP_NONE_OPTION = "p2p.ui.group_none_option";
    public static final String UI_NO_GROUP = "p2p.ui.no_group";

    // ---- NetworkScreen ----
    public static final String UI_SELECT_NETWORK = "p2p.ui.select_network";
    public static final String UI_CHANNEL_COUNT = "p2p.ui.channel_count";
    public static final String UI_ENTER_NETWORK = "p2p.ui.enter_network";
    public static final String UI_BACK = "p2p.ui.back";
    public static final String UI_SHARE_HINT = "p2p.ui.share_hint";
    public static final String UI_NO_NETWORK = "p2p.ui.no_network";

    // ---- HUD (bind-mode status bar + tooltips) ----
    public static final String HUD_BIND = "p2p.hud.bind";
    public static final String HUD_BIND_CHANNEL = "p2p.hud.bind_channel";
    public static final String HUD_BIND_NO_CHANNEL = "p2p.hud.bind_no_channel";
    public static final String HUD_HINT = "p2p.hud.hint";
    public static final String HUD_DIMENSION = "p2p.hud.dimension";
    public static final String HUD_BLOCK = "p2p.hud.block";

    // ---- server chat messages (ChannelManager) ----
    public static final String MSG_CHANNEL_GONE = "p2p.msg.channel_gone";
    public static final String MSG_NO_PERM = "p2p.msg.no_perm";
    public static final String MSG_ENDPOINT_REMOVED = "p2p.msg.endpoint_removed";
    public static final String MSG_ENDPOINT_ADDED = "p2p.msg.endpoint_added";
    public static final String MSG_ROLE_IN = "p2p.msg.role_in";
    public static final String MSG_ROLE_OUT = "p2p.msg.role_out";
    public static final String MSG_CHAIN_NONE = "p2p.msg.chain_none";
    public static final String MSG_CHAIN_ADDED = "p2p.msg.chain_added";
    public static final String MSG_UNCHAIN_NONE = "p2p.msg.unchain_none";
    public static final String MSG_UNCHAIN_REMOVED = "p2p.msg.unchain_removed";
    /** 绑定点击回显的后缀：当前频道端点总数（1.3.1）。 */
    public static final String MSG_ENDPOINT_TOTAL = "p2p.msg.endpoint_total";
    /** 删除组级联删除组内频道的聊天回显（1.4.0）。 */
    public static final String MSG_GROUP_DELETED = "p2p.msg.group_deleted";

    // ---- bind-mode + packet chat messages ----
    public static final String MSG_BIND_ON = "p2p.msg.bind_on";
    public static final String MSG_BIND_ON_NO_CHANNEL = "p2p.msg.bind_on_no_channel";
    public static final String MSG_BIND_OFF = "p2p.msg.bind_off";
    public static final String MSG_NO_BOUND_CHANNEL = "p2p.msg.no_bound_channel";
    public static final String MSG_NO_PERM_GROUP = "p2p.msg.no_perm_group";
    public static final String MSG_ENDPOINT_REMOVED_KEY = "p2p.msg.endpoint_removed_key";
    public static final String MSG_CLEARED_AIR = "p2p.msg.cleared_air";
    public static final String MSG_NO_AIR = "p2p.msg.no_air";
    public static final String MSG_CHUNKLOAD_DISABLED = "p2p.msg.chunkload_disabled";
    public static final String MSG_CHUNKLOAD_NO_PERM = "p2p.msg.chunkload_no_perm";
    public static final String MSG_CHUNKLOAD_ON = "p2p.msg.chunkload_on";
    public static final String MSG_CHUNKLOAD_OFF = "p2p.msg.chunkload_off";
    public static final String MSG_BIND_NOT_ACCESSIBLE = "p2p.msg.bind_not_accessible";
    public static final String MSG_BIND_CLEARED = "p2p.msg.bind_cleared";
    public static final String MSG_BIND_SET = "p2p.msg.bind_set";

    // ---- command (/p2p network-sharing) ----
    public static final String CMD_USAGE_HEADER = "p2p.cmd.usage.header";
    public static final String CMD_USAGE_INVITE = "p2p.cmd.usage.invite";
    public static final String CMD_USAGE_ACCEPT = "p2p.cmd.usage.accept";
    public static final String CMD_USAGE_DENY = "p2p.cmd.usage.deny";
    public static final String CMD_USAGE_LEAVE = "p2p.cmd.usage.leave";
    public static final String CMD_USAGE_KICK = "p2p.cmd.usage.kick";
    public static final String CMD_USAGE_MEMBERS = "p2p.cmd.usage.members";
    public static final String CMD_USAGE_INVITES = "p2p.cmd.usage.invites";

    public static final String CMD_INVITE_SELF = "p2p.cmd.invite.self";
    public static final String CMD_INVITE_ALREADY = "p2p.cmd.invite.already";
    public static final String CMD_INVITE_PENDING = "p2p.cmd.invite.pending";
    public static final String CMD_INVITE_SENT = "p2p.cmd.invite.sent";
    public static final String CMD_INVITE_NOTICE = "p2p.cmd.invite.notice";

    public static final String CMD_ACCEPT_LABEL = "p2p.cmd.accept.label";
    public static final String CMD_DENY_LABEL = "p2p.cmd.deny.label";
    public static final String CMD_ACCEPT_HOVER = "p2p.cmd.accept.hover";
    public static final String CMD_DENY_HOVER = "p2p.cmd.deny.hover";

    public static final String CMD_ACCEPT_NO_PENDING = "p2p.cmd.accept.no_pending";
    public static final String CMD_ACCEPT_SELF = "p2p.cmd.accept.self";
    public static final String CMD_ACCEPT_NONE_FROM = "p2p.cmd.accept.none_from";
    public static final String CMD_ACCEPT_JOINED = "p2p.cmd.accept.joined";
    public static final String CMD_ACCEPT_ACCEPTED = "p2p.cmd.accept.accepted";

    public static final String CMD_DENY_NONE_FROM = "p2p.cmd.deny.none_from";
    public static final String CMD_DENY_DONE = "p2p.cmd.deny.done";
    public static final String CMD_DENY_DENIED = "p2p.cmd.deny.denied";

    public static final String CMD_UNLINK_USAGE = "p2p.cmd.unlink.usage";
    public static final String CMD_UNLINK_SELF_LEAVE = "p2p.cmd.unlink.self_leave";
    public static final String CMD_UNLINK_SELF_KICK = "p2p.cmd.unlink.self_kick";
    public static final String CMD_UNLINK_NOT_JOINED = "p2p.cmd.unlink.not_joined";
    public static final String CMD_UNLINK_NOT_MANAGED = "p2p.cmd.unlink.not_managed";
    public static final String CMD_UNLINK_LEFT = "p2p.cmd.unlink.left";
    public static final String CMD_UNLINK_KICKED = "p2p.cmd.unlink.kicked";
    public static final String CMD_UNLINK_LEFT_NOTIFY = "p2p.cmd.unlink.left_notify";
    public static final String CMD_UNLINK_KICKED_NOTIFY = "p2p.cmd.unlink.kicked_notify";

    public static final String CMD_MEMBERS_HEADER_MINE = "p2p.cmd.members.header_mine";
    public static final String CMD_MEMBERS_HEADER_SHARED = "p2p.cmd.members.header_shared";
    public static final String CMD_MEMBERS_TAG_OWNER = "p2p.cmd.members.tag_owner";
    public static final String CMD_MEMBERS_TAG_MEMBER = "p2p.cmd.members.tag_member";
    public static final String CMD_MEMBERS_PENDING_PREFIX = "p2p.cmd.members.pending_prefix";
    public static final String CMD_MEMBERS_NONE = "p2p.cmd.members.none";

    public static final String CMD_INVITES_NONE_RECEIVED = "p2p.cmd.invites.none_received";
    public static final String CMD_INVITES_LINE = "p2p.cmd.invites.line";
    public static final String CMD_INVITES_SENT_PREFIX = "p2p.cmd.invites.sent_prefix";
    public static final String CMD_INVITES_HINT = "p2p.cmd.invites.hint";
}
