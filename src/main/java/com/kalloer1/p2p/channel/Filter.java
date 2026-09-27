package com.kalloer1.p2p.channel;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Sampling filter for a channel member. Energy channels have no filter (always pass).
 * Holds item templates, item tags, an optional NBT regex, and/or fluid templates depending on channel type.
 *
 * <p>Addons may store arbitrary namespaced data in {@link #addonData}: it is serialized and synced
 * verbatim (inside the filter NBT, so the SET_FILTER packet is unchanged) and is never interpreted
 * by p2p itself. Old saves without the field deserialize to an empty map.</p>
 */
public class Filter {
    public enum Mode { WHITELIST, BLACKLIST }

    private final Mode mode;
    private final boolean matchNBT;
    private final List<ItemStack> itemTemplates;
    private final List<ResourceLocation> itemTags;
    private final String nbtRegex;
    private final List<FluidStack> fluidTemplates;

    /**
     * Pass-through storage for addon filters, keyed by the addon's own namespace
     * (e.g. {@code "mekanism:gas"}). p2p only carries, saves and syncs these tags.
     */
    public final Map<String, CompoundTag> addonData = new LinkedHashMap<>();

    public Filter(Mode mode, boolean matchNBT, List<ItemStack> itemTemplates,
                  List<ResourceLocation> itemTags, String nbtRegex, List<FluidStack> fluidTemplates) {
        this.mode = mode;
        this.matchNBT = matchNBT;
        this.itemTemplates = itemTemplates;
        this.itemTags = itemTags;
        this.nbtRegex = nbtRegex == null ? "" : nbtRegex;
        this.fluidTemplates = fluidTemplates;
    }

    public static Filter itemFilter(Mode mode, boolean matchNBT, List<ItemStack> templates,
                                    List<ResourceLocation> tags, String nbtRegex) {
        return new Filter(mode, matchNBT, new ArrayList<>(templates), new ArrayList<>(tags), nbtRegex, new ArrayList<>());
    }

    public static Filter fluidFilter(Mode mode, List<FluidStack> templates) {
        return new Filter(mode, false, new ArrayList<>(), new ArrayList<>(), "", new ArrayList<>(templates));
    }

    /** Energy channels: no filtering possible. */
    public static Filter energyPass() {
        return new Filter(Mode.WHITELIST, false, new ArrayList<>(), new ArrayList<>(), "", new ArrayList<>());
    }

    public Mode getMode() { return mode; }
    public boolean isMatchNBT() { return matchNBT; }
    public List<ItemStack> getItemTemplates() { return itemTemplates; }
    public List<ResourceLocation> getItemTags() { return itemTags; }
    public String getNbtRegex() { return nbtRegex; }
    public List<FluidStack> getFluidTemplates() { return fluidTemplates; }
    public boolean isEmpty() {
        return itemTemplates.isEmpty() && itemTags.isEmpty() && nbtRegex.isEmpty() && fluidTemplates.isEmpty()
                && addonData.isEmpty();
    }

    public boolean test(ItemStack stack) {
        if (isEmpty()) return true;
        boolean anyMatch = false;

        // template match
        if (!anyMatch) {
            for (ItemStack t : itemTemplates) {
                boolean same = matchNBT ? ItemStack.isSameItemSameComponents(t, stack) : ItemStack.isSameItem(t, stack);
                if (same) { anyMatch = true; break; }
            }
        }

        // tag match
        if (!anyMatch) {
            for (ResourceLocation tagId : itemTags) {
                TagKey<Item> key = ItemTags.create(tagId);
                if (stack.is(key)) { anyMatch = true; break; }
            }
        }

        // nbt regex match — in 1.21.1 item NBT lives inside DataComponents.CUSTOM_DATA
        if (!anyMatch && !nbtRegex.isEmpty()) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && !customData.isEmpty()) {
                try {
                    Pattern p = Pattern.compile(nbtRegex);
                    if (p.matcher(customData.copyTag().toString()).find()) anyMatch = true;
                } catch (PatternSyntaxException ignored) {}
            }
        }

        return mode == Mode.WHITELIST ? anyMatch : !anyMatch;
    }

    public boolean test(FluidStack stack) {
        if (isEmpty()) return true;
        boolean anyMatch = false;
        for (FluidStack t : fluidTemplates) {
            boolean same = matchNBT
                    ? FluidStack.isSameFluidSameComponents(t, stack)
                    : t.getFluid() == stack.getFluid();
            if (same) { anyMatch = true; break; }
        }
        return mode == Mode.WHITELIST ? anyMatch : !anyMatch;
    }

    public boolean testEnergy() { return true; }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putBoolean("matchNBT", matchNBT);
        ListTag items = new ListTag();
        for (ItemStack s : itemTemplates) items.add(s.save(provider));
        tag.put("items", items);
        ListTag tags = new ListTag();
        for (ResourceLocation r : itemTags) tags.add(net.minecraft.nbt.StringTag.valueOf(r.toString()));
        tag.put("tags", tags);
        tag.putString("nbtRegex", nbtRegex);
        ListTag fluids = new ListTag();
        for (FluidStack f : fluidTemplates) fluids.add(f.save(provider));
        tag.put("fluids", fluids);
        if (!addonData.isEmpty()) {
            CompoundTag addons = new CompoundTag();
            for (Map.Entry<String, CompoundTag> e : addonData.entrySet())
                if (e.getValue() != null && !e.getValue().isEmpty()) addons.put(e.getKey(), e.getValue());
            if (!addons.isEmpty()) tag.put("addon", addons);
        }
        return tag;
    }

    public static Filter deserializeNBT(CompoundTag tag, HolderLookup.Provider provider) {
        Mode m = Mode.valueOf(tag.getString("mode"));
        boolean nbt = tag.getBoolean("matchNBT");
        List<ItemStack> items = new ArrayList<>();
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND))
            ItemStack.parse(provider, (CompoundTag) t).ifPresent(items::add);
        List<ResourceLocation> tags = new ArrayList<>();
        for (Tag t : tag.getList("tags", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) tags.add(id);
        }
        String regex = tag.contains("nbtRegex", Tag.TAG_STRING) ? tag.getString("nbtRegex") : "";
        List<FluidStack> fluids = new ArrayList<>();
        for (Tag t : tag.getList("fluids", Tag.TAG_COMPOUND))
            FluidStack.parse(provider, (CompoundTag) t).ifPresent(fluids::add);
        Filter f = new Filter(m, nbt, items, tags, regex, fluids);
        // addon pass-through: old saves simply have no "addon" section → empty map
        if (tag.contains("addon", Tag.TAG_COMPOUND)) {
            CompoundTag addons = tag.getCompound("addon");
            for (String key : addons.getAllKeys())
                if (addons.get(key) instanceof CompoundTag ct) f.addonData.put(key, ct);
        }
        return f;
    }
}
