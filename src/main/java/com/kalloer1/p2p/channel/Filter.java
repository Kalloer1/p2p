package com.kalloer1.p2p.channel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Sampling filter for a channel member. Energy channels have no filter (always pass).
 * Holds item templates, item tags, an optional NBT regex, and/or fluid templates depending on channel type.
 */
public class Filter {
    public enum Mode { WHITELIST, BLACKLIST }

    private final Mode mode;
    private final boolean matchNBT;
    private final List<ItemStack> itemTemplates;
    private final List<ResourceLocation> itemTags;
    private final String nbtRegex;
    private final List<FluidStack> fluidTemplates;

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
        return itemTemplates.isEmpty() && itemTags.isEmpty() && nbtRegex.isEmpty() && fluidTemplates.isEmpty();
    }

    public boolean test(ItemStack stack) {
        if (isEmpty()) return true;
        boolean anyMatch = false;

        // template match
        if (!anyMatch) {
            for (ItemStack t : itemTemplates) {
                boolean same = matchNBT ? ItemStack.isSameItemSameTags(t, stack) : ItemStack.isSameItem(t, stack);
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

        // nbt regex match
        if (!anyMatch && !nbtRegex.isEmpty() && stack.hasTag()) {
            try {
                Pattern p = Pattern.compile(nbtRegex);
                if (p.matcher(stack.getTag().toString()).find()) anyMatch = true;
            } catch (PatternSyntaxException ignored) {}
        }

        return mode == Mode.WHITELIST ? anyMatch : !anyMatch;
    }

    public boolean test(FluidStack stack) {
        if (isEmpty()) return true;
        boolean anyMatch = false;
        for (FluidStack t : fluidTemplates) {
            boolean same = t.getFluid() == stack.getFluid()
                    && (!matchNBT || t.getTag() == null || t.getTag().equals(stack.getTag()));
            if (same) { anyMatch = true; break; }
        }
        return mode == Mode.WHITELIST ? anyMatch : !anyMatch;
    }

    public boolean testEnergy() { return true; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name());
        tag.putBoolean("matchNBT", matchNBT);
        ListTag items = new ListTag();
        for (ItemStack s : itemTemplates) items.add(s.save(new CompoundTag()));
        tag.put("items", items);
        ListTag tags = new ListTag();
        for (ResourceLocation r : itemTags) tags.add(net.minecraft.nbt.StringTag.valueOf(r.toString()));
        tag.put("tags", tags);
        tag.putString("nbtRegex", nbtRegex);
        ListTag fluids = new ListTag();
        for (FluidStack f : fluidTemplates) fluids.add(f.writeToNBT(new CompoundTag()));
        tag.put("fluids", fluids);
        return tag;
    }

    public static Filter deserializeNBT(CompoundTag tag) {
        Mode m = Mode.valueOf(tag.getString("mode"));
        boolean nbt = tag.getBoolean("matchNBT");
        List<ItemStack> items = new ArrayList<>();
        for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) items.add(ItemStack.of((CompoundTag) t));
        List<ResourceLocation> tags = new ArrayList<>();
        for (Tag t : tag.getList("tags", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(t.getAsString());
            if (id != null) tags.add(id);
        }
        String regex = tag.contains("nbtRegex", Tag.TAG_STRING) ? tag.getString("nbtRegex") : "";
        List<FluidStack> fluids = new ArrayList<>();
        for (Tag t : tag.getList("fluids", Tag.TAG_COMPOUND)) fluids.add(FluidStack.loadFluidStackFromNBT((CompoundTag) t));
        return new Filter(m, nbt, items, tags, regex, fluids);
    }
}
