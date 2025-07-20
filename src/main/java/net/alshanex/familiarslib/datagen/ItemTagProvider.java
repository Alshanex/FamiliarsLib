package net.alshanex.familiarslib.datagen;

import io.redspace.ironsspellbooks.util.ModTags;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

public class ItemTagProvider extends ItemTagsProvider {
    public static final TagKey<Item> SOUND_FOCUS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "sound_focus"));
    public ItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(SOUND_FOCUS).add(Items.NOTE_BLOCK);
        tag(ModTags.SCHOOL_FOCUS).add(Items.NOTE_BLOCK);
    }
}
